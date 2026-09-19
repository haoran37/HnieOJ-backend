#!/usr/bin/env bash

set -Eeuo pipefail

#######################################
# 全局部署变量。
# 如需调整分支、端口、仓库或目录，优先修改这里。
#######################################
DEPLOY_BRANCH="${DEPLOY_BRANCH:-dev}"
GATEWAY_PUBLIC_PORT="${GATEWAY_PUBLIC_PORT:-8800}"
GATEWAY_SERVER_PORT="${GATEWAY_SERVER_PORT:-8800}"
JAVA_BASE_IMAGE="${JAVA_BASE_IMAGE:-eclipse-temurin:17-jre-jammy}"

GIT_REPO_URL="${GIT_REPO_URL:-https://github.com/haoran37/HnieOJ-backend.git}"
GIT_USERNAME="${GIT_USERNAME:-x-access-token}"
GIT_TOKEN="${GIT_TOKEN:-${GIT_AUTH_TOKEN:-}}"
GOJUDGE_GIT_REPO_URL="${GOJUDGE_GIT_REPO_URL:-https://github.com/haoran37/go-judge.git}"
# 默认跟随上游 go-judge 的开发分支策略（develop），不再使用已退休的 master。
GOJUDGE_BRANCH="${GOJUDGE_BRANCH:-develop}"

DEPLOY_DIR="${DEPLOY_DIR:-/opt/hnieoj/backend}"
SOURCE_DIR="${SOURCE_DIR:-${DEPLOY_DIR}/source}"
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-hnieoj-dev}"
COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-2}"
DISCARD_LOCAL_CHANGES="${DISCARD_LOCAL_CHANGES:-true}"

PROBLEM_STORAGE_DIR="${PROBLEM_STORAGE_DIR:-/data/oj/problems}"
# 后端判题安全材料只读挂载目录（保留兼容）；节点 Ed25519 私钥不在此目录，
# 而是由节点自身生成并保存在判题节点状态目录 GOJUDGE_STATE_DIR。
JUDGE_SECURITY_DIR="${JUDGE_SECURITY_DIR:-/etc/hnieoj/judge-security}"
GOJUDGE_DEPLOY_DIR="${GOJUDGE_DEPLOY_DIR:-/opt/hnieoj/go-judge}"
GOJUDGE_SOURCE_DIR="${GOJUDGE_SOURCE_DIR:-${GOJUDGE_DEPLOY_DIR}/source}"
# 以下宿主/容器挂载路径统一由 resolve_gojudge_paths 解析：
# 显式进程环境（GOJUDGE_* 或 HNIEOJ_* compose 变量）> 安全解析 .env > 内置默认；绝不 source .env。
# 这里只保留空占位，避免内置默认值在解析前覆盖 .env 里的 HNIEOJ_* 配置。
GOJUDGE_CONFIG_HOST_FILE="${GOJUDGE_CONFIG_HOST_FILE:-}"
HNIEOJ_JUDGE_STATE_HOST_DIR="${HNIEOJ_JUDGE_STATE_HOST_DIR:-}"
HNIEOJ_JUDGE_BOOTSTRAP_HOST_DIR="${HNIEOJ_JUDGE_BOOTSTRAP_HOST_DIR:-}"
GOJUDGE_CONFIG_DIR="${GOJUDGE_CONFIG_DIR:-}"
GOJUDGE_CONFIG_FILE="${GOJUDGE_CONFIG_FILE:-}"
GOJUDGE_CACHE_DIR="${GOJUDGE_CACHE_DIR:-}"
# 判题节点状态目录：identity.json（本地 Ed25519 私钥）/config.yaml/results，持久化且必须可写。
GOJUDGE_STATE_DIR="${GOJUDGE_STATE_DIR:-}"
# 一次性 Bootstrap 明文（0600）及其 0700 目录，由运维在管理员签发后放置。
GOJUDGE_BOOTSTRAP_DIR="${GOJUDGE_BOOTSTRAP_DIR:-}"
GOJUDGE_BOOTSTRAP_FILE=""

MAVEN_SETTINGS_FILE="deploy/maven/settings.xml"
MAVEN_COMMAND="${MAVEN_COMMAND:-mvn -s ${MAVEN_SETTINGS_FILE} clean package -DskipTests}"
COMPOSE_FILE="deploy/docker/docker-compose.dev.yml"
GOJUDGE_COMPOSE_FILE="deploy/docker/docker-compose.gojudge.yml"
ENV_TEMPLATE_FILE="deploy/docker/.env.example"
LOG_TAIL="${LOG_TAIL:-200}"

GIT_ASKPASS_FILE=""

cleanup() {
  if [[ -n "${GIT_ASKPASS_FILE}" && -f "${GIT_ASKPASS_FILE}" ]]; then
    rm -f "${GIT_ASKPASS_FILE}"
  fi
}

trap cleanup EXIT

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
  printf '错误：%s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<EOF
用法：
  bash deploy/scripts/deploy-dev.sh [命令] [服务名...]

命令：
  deploy              拉取代码、生成必要安全材料、打包并重新部署，默认命令
  pull                只拉取 ${DEPLOY_BRANCH} 最新代码
  build               拉取代码并执行 Maven 打包
  up [服务名...]      使用 Docker Compose 构建并启动服务
  ps                  查看容器状态
  logs [服务名...]    查看日志，默认跟随全部服务日志
  restart [服务名...] 重启服务，不传服务名则重启全部服务
  stop [服务名...]    停止服务，不传服务名则停止全部服务
  down                停止并移除 Compose 容器
  gojudge-up          拉取、构建并启动 go-judge 沙箱与判题节点
  gojudge-ps          查看 go-judge 容器状态
  gojudge-logs        查看 go-judge 日志
  gojudge-cache-status 查看 go-judge 测试数据缓存占用
  gojudge-cache-clean [天数] 清理 N 天未修改的 go-judge 测试数据缓存，默认 7 天
  gojudge-down        停止并移除 go-judge 容器
  security-init       只生成/补齐 JWT Secret 与节点运行时密钥
  help                显示帮助

常用示例：
  bash deploy/scripts/deploy-dev.sh
  bash deploy/scripts/deploy-dev.sh logs gateway
  bash deploy/scripts/deploy-dev.sh restart hnieoj-user
  bash deploy/scripts/deploy-dev.sh gojudge-up
EOF
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令：$1"
  log "命令检查通过：$1"
}

require_file() {
  [[ -f "$1" ]] || fail "缺少文件：$1"
  log "文件检查通过：$1"
}

setup_git_auth() {
  if [[ "${GIT_REPO_URL}" != http* && "${GOJUDGE_GIT_REPO_URL}" != http* ]]; then
    return
  fi

  export GIT_TERMINAL_PROMPT=0
  if [[ -z "${GIT_TOKEN}" ]]; then
    log "未配置 GIT_TOKEN。若仓库为私有仓库，拉取代码会失败。"
    return
  fi

  GIT_ASKPASS_FILE="$(mktemp)"
  cat > "${GIT_ASKPASS_FILE}" <<'EOF'
#!/usr/bin/env bash
case "$1" in
  *Username*) printf '%s\n' "${GIT_USERNAME:-x-access-token}" ;;
  *Password*) printf '%s\n' "${GIT_TOKEN}" ;;
  *) printf '\n' ;;
esac
EOF
  chmod 700 "${GIT_ASKPASS_FILE}"
  export GIT_ASKPASS="${GIT_ASKPASS_FILE}"
  export GIT_USERNAME
  export GIT_TOKEN
}

upsert_env_value() {
  local key="$1"
  local value="$2"
  local tmp_file

  mkdir -p "$(dirname "${ENV_FILE}")"
  touch "${ENV_FILE}"
  tmp_file="$(mktemp)"
  awk -v k="${key}" -v v="${value}" '
    BEGIN { found = 0 }
    $0 ~ "^" k "=" { print k "=" v; found = 1; next }
    { print }
    END { if (found == 0) print k "=" v }
  ' "${ENV_FILE}" > "${tmp_file}"
  mv "${tmp_file}" "${ENV_FILE}"
  chmod 600 "${ENV_FILE}"
}

env_value() {
  local key="$1"
  grep -E "^${key}=" "${ENV_FILE}" 2>/dev/null | tail -n 1 | cut -d= -f2- || true
}

# 解析单个宿主机路径：显式环境变量 > .env（安全解析，绝不 source）> 内置默认。
resolve_path() {
  local explicit="$1"
  local env_key="$2"
  local default_value="$3"
  local from_env

  if [[ -n "${explicit}" ]]; then
    printf '%s' "${explicit}"
    return
  fi
  from_env="$(env_value "${env_key}")"
  if [[ -n "${from_env}" ]]; then
    printf '%s' "${from_env}"
    return
  fi
  printf '%s' "${default_value}"
}

# 统一解析 go-judge 状态/配置/Bootstrap/缓存挂载路径，并同步为 Compose 插值变量。
# 幂等：解析后的非空值再次进入时按显式值保留。
resolve_gojudge_paths() {
  GOJUDGE_STATE_DIR="$(resolve_path "${GOJUDGE_STATE_DIR:-${HNIEOJ_JUDGE_STATE_HOST_DIR:-}}" \
    HNIEOJ_JUDGE_STATE_HOST_DIR /data/oj/judge-node)"
  # 配置 bind 源文件：显式 FILE / CONFIG_HOST_FILE > 显式 CONFIG_DIR/config.yaml >
  # .env 中 CONFIG_HOST_FILE > 内置默认；保留显式 GOJUDGE_CONFIG_DIR 的既有兼容语义。
  local config_explicit="${GOJUDGE_CONFIG_FILE:-${GOJUDGE_CONFIG_HOST_FILE:-}}"
  if [[ -z "${config_explicit}" && -n "${GOJUDGE_CONFIG_DIR}" ]]; then
    config_explicit="${GOJUDGE_CONFIG_DIR}/config.yaml"
  fi
  GOJUDGE_CONFIG_FILE="$(resolve_path "${config_explicit}" \
    GOJUDGE_CONFIG_HOST_FILE /etc/hnieoj/go-judge/config.yaml)"
  GOJUDGE_BOOTSTRAP_DIR="$(resolve_path "${GOJUDGE_BOOTSTRAP_DIR:-${HNIEOJ_JUDGE_BOOTSTRAP_HOST_DIR:-}}" \
    HNIEOJ_JUDGE_BOOTSTRAP_HOST_DIR /etc/hnieoj/judge-node)"
  GOJUDGE_CACHE_DIR="$(resolve_path "${GOJUDGE_CACHE_DIR:-}" \
    GOJUDGE_CACHE_DIR /data/oj/judge-cache)"

  GOJUDGE_CONFIG_DIR="$(dirname "${GOJUDGE_CONFIG_FILE}")"
  GOJUDGE_BOOTSTRAP_FILE="${GOJUDGE_BOOTSTRAP_DIR}/bootstrap.token"
  GOJUDGE_CONFIG_HOST_FILE="${GOJUDGE_CONFIG_FILE}"
  HNIEOJ_JUDGE_STATE_HOST_DIR="${GOJUDGE_STATE_DIR}"
  HNIEOJ_JUDGE_BOOTSTRAP_HOST_DIR="${GOJUDGE_BOOTSTRAP_DIR}"
}

is_blank_or_placeholder() {
  local value="$1"
  [[ -z "${value}" || "${value}" == "replace_me" || "${value}" == *"replace_me"* ]]
}

openssl_random_urlsafe() {
  openssl rand -base64 "$1" | tr '+/' '-_' | tr -d '=\n'
}

ensure_judge_security_materials() {
  # 旧 RSA 密钥对 / 共享 formal-token / Nacos 密钥分发流程已退休：
  # 节点身份统一走 Bootstrap + Ed25519，长期密钥由节点本地生成并以公钥注册。
  # NodeAccess 秘密（HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET）只允许运行时环境/文件注入，
  # 缺失或为占位符时后端启动 fail-fast；本函数只在服务器 .env 中生成随机值，绝不写入 Nacos 模板。
  require_command openssl

  if [[ ! -f "${ENV_FILE}" ]]; then
    return
  fi

  local jwt_secret
  local node_access_secret
  local internal_token
  jwt_secret="$(env_value HNIEOJ_JUDGE_JWT_SECRET)"
  node_access_secret="$(env_value HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET)"
  internal_token="$(env_value HNIEOJ_INTERNAL_TOKEN)"

  if is_blank_or_placeholder "${jwt_secret}"; then
    upsert_env_value "HNIEOJ_JUDGE_JWT_SECRET" "$(openssl_random_urlsafe 64)"
    log "已写入 HNIEOJ_JUDGE_JWT_SECRET"
  fi
  if is_blank_or_placeholder "${node_access_secret}"; then
    upsert_env_value "HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET" "$(openssl_random_urlsafe 48)"
    log "已写入 HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET（仅运行时环境注入）"
  fi
  if is_blank_or_placeholder "${internal_token}"; then
    upsert_env_value "HNIEOJ_INTERNAL_TOKEN" "$(openssl_random_urlsafe 48)"
    log "已写入 HNIEOJ_INTERNAL_TOKEN"
  fi
  log "迁移提示：正式判题节点不再使用 RSA/共享 formalToken，请通过 Bootstrap + Ed25519 注册新身份。"
}

prepare_env_template_if_missing() {
  if [[ -f "${ENV_FILE}" ]]; then
    return
  fi

  mkdir -p "$(dirname "${ENV_FILE}")"
  if [[ -f "${SOURCE_DIR}/${ENV_TEMPLATE_FILE}" ]]; then
    cp "${SOURCE_DIR}/${ENV_TEMPLATE_FILE}" "${ENV_FILE}"
  elif [[ -f "${ENV_TEMPLATE_FILE}" ]]; then
    cp "${ENV_TEMPLATE_FILE}" "${ENV_FILE}"
  else
    touch "${ENV_FILE}"
  fi
  chmod 600 "${ENV_FILE}"
  log "已创建 ${ENV_FILE}"
}

check_runtime_environment() {
  log "开始检查运行环境"
  log "部署分支：${DEPLOY_BRANCH}"
  log "代码仓库：${GIT_REPO_URL}"
  log "网关端口映射：${GATEWAY_PUBLIC_PORT}:${GATEWAY_SERVER_PORT}"
  log "Java 基础镜像：${JAVA_BASE_IMAGE}"
  log "丢弃服务器本地源码改动：${DISCARD_LOCAL_CHANGES}"
  log "当前用户：$(id)"

  require_command git
  require_command mvn
  require_command docker
  require_command openssl

  docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 不可用"
  docker info >/dev/null 2>&1 || fail "当前用户无法访问 Docker daemon"

  resolve_gojudge_paths
  mkdir -p "${DEPLOY_DIR}" "${PROBLEM_STORAGE_DIR}" "${JUDGE_SECURITY_DIR}" "${GOJUDGE_CACHE_DIR}" \
    "${GOJUDGE_STATE_DIR}" "${GOJUDGE_BOOTSTRAP_DIR}"
  chmod 700 "${GOJUDGE_STATE_DIR}" "${GOJUDGE_BOOTSTRAP_DIR}"
  log "目录检查通过：${PROBLEM_STORAGE_DIR}"
  log "后端判题安全材料只读挂载目录：${JUDGE_SECURITY_DIR}（节点私钥不在此目录）"
  log "判题节点状态目录：${GOJUDGE_STATE_DIR}（0700，保存 identity.json/results，必须可写）"
  log "判题节点 Bootstrap 目录：${GOJUDGE_BOOTSTRAP_DIR}（0700，一次性明文文件保持 0600）"
}

check_docker_environment() {
  require_command docker
  docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 不可用"
  docker info >/dev/null 2>&1 || fail "当前用户无法访问 Docker daemon"
}

sync_git_repo() {
  local repo_url="$1"
  local branch="$2"
  local target_dir="$3"
  local label="$4"

  log "开始同步 ${label} 源码"
  setup_git_auth

  if [[ ! -d "${target_dir}/.git" ]]; then
    if [[ -d "${target_dir}" && -n "$(find "${target_dir}" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
      fail "${label} 源码目录不是 Git 仓库且非空：${target_dir}"
    fi
    mkdir -p "$(dirname "${target_dir}")"
    git clone --branch "${branch}" --single-branch "${repo_url}" "${target_dir}"
  fi

  cd "${target_dir}"
  if [[ "${DISCARD_LOCAL_CHANGES}" == "true" ]]; then
    log "${label} 部署目录只作为运行环境使用，将丢弃服务器本地源码改动。"
    git reset --hard
    git clean -fd
  elif ! git diff --quiet || ! git diff --cached --quiet || [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
    fail "${label} 源码目录存在本地改动：${target_dir}"
  fi

  git remote set-url origin "${repo_url}"
  git fetch --prune origin "${branch}"
  git checkout "${branch}"
  git reset --hard "origin/${branch}"
  log "${label} 源码版本：$(git rev-parse --short HEAD)"
}

sync_source_code() {
  sync_git_repo "${GIT_REPO_URL}" "${DEPLOY_BRANCH}" "${SOURCE_DIR}" "backend"
}

sync_gojudge_source_code() {
  sync_git_repo "${GOJUDGE_GIT_REPO_URL}" "${GOJUDGE_BRANCH}" "${GOJUDGE_SOURCE_DIR}" "go-judge"
}

ensure_source_ready() {
  [[ -d "${SOURCE_DIR}/.git" ]] || fail "源码目录不存在，请先执行 deploy"
  cd "${SOURCE_DIR}"
  require_file "${COMPOSE_FILE}"
  require_file "${ENV_FILE}"
}

prepare_environment_file() {
  log "检查部署环境变量文件"
  cd "${SOURCE_DIR}"
  require_file "${ENV_TEMPLATE_FILE}"

  prepare_env_template_if_missing

  require_file "${ENV_FILE}"
  upsert_env_value "GATEWAY_PUBLIC_PORT" "${GATEWAY_PUBLIC_PORT}"
  upsert_env_value "GATEWAY_SERVER_PORT" "${GATEWAY_SERVER_PORT}"
  upsert_env_value "JAVA_BASE_IMAGE" "${JAVA_BASE_IMAGE}"
  ensure_judge_security_materials

  if grep -Eq '^(MYSQL_PASSWORD|REDIS_PASSWORD)=($|replace_me)' "${ENV_FILE}"; then
    fail "${ENV_FILE} 仍包含未填写的 MySQL/Redis 密码，请填写后重试。"
  fi
}

build_project() {
  log "开始 Maven 打包"
  cd "${SOURCE_DIR}"
  require_file "${MAVEN_SETTINGS_FILE}"
  ${MAVEN_COMMAND}
}

compose() {
  docker compose -p "${COMPOSE_PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

gojudge_compose() {
  docker compose -p "${COMPOSE_PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${GOJUDGE_COMPOSE_FILE}" "$@"
}

export_compose_variables() {
  export GATEWAY_PUBLIC_PORT GATEWAY_SERVER_PORT JAVA_BASE_IMAGE COMPOSE_PARALLEL_LIMIT
}

export_gojudge_variables() {
  resolve_gojudge_paths
  export GOJUDGE_SOURCE_DIR
  export GOJUDGE_CONFIG_HOST_FILE
  export GOJUDGE_CACHE_DIR
  export HNIEOJ_JUDGE_STATE_HOST_DIR
  export HNIEOJ_JUDGE_BOOTSTRAP_HOST_DIR
}

deploy_compose() {
  log "开始部署 Docker Compose 服务"
  cd "${SOURCE_DIR}"
  require_file "${COMPOSE_FILE}"
  export_compose_variables
  compose up -d --build --remove-orphans "$@"
  compose ps
}

show_status() {
  check_docker_environment
  ensure_source_ready
  export_compose_variables
  compose ps
}

show_logs() {
  check_docker_environment
  ensure_source_ready
  export_compose_variables
  compose logs -f --tail="${LOG_TAIL}" "$@"
}

restart_services() {
  check_docker_environment
  ensure_source_ready
  export_compose_variables
  compose restart "$@"
  compose ps
}

stop_services() {
  check_docker_environment
  ensure_source_ready
  export_compose_variables
  compose stop "$@"
}

down_services() {
  check_docker_environment
  ensure_source_ready
  export_compose_variables
  compose down
}

prepare_gojudge_config_file() {
  resolve_gojudge_paths
  mkdir -p "${GOJUDGE_CONFIG_DIR}"
  if [[ ! -f "${GOJUDGE_CONFIG_FILE}" ]]; then
    require_file "${GOJUDGE_SOURCE_DIR}/deploy/config.formal.example.yaml"
    cp "${GOJUDGE_SOURCE_DIR}/deploy/config.formal.example.yaml" "${GOJUDGE_CONFIG_FILE}"
    chmod 600 "${GOJUDGE_CONFIG_FILE}"
    fail "已创建 ${GOJUDGE_CONFIG_FILE}，请同时填写 hnieoj.baseUrl（必须 HTTPS）、\
hnieoj.wssUrl（任务通道必须 WSS）与 hnieoj.audience（必须等于后端 HNIEOJ_JUDGE_NODE_AUDIENCE，\
当前默认 hnieoj-judge-node），以及节点类型/并发与 gojudge.endpoint 后重新执行 gojudge-up；\
正式/临时节点机制相同，仅 node.type 与授权不同。"
  fi
  require_file "${GOJUDGE_CONFIG_FILE}"
  if grep -Eq 'replace_me|password: ""' "${GOJUDGE_CONFIG_FILE}"; then
    fail "${GOJUDGE_CONFIG_FILE} 仍包含占位配置，请填写真实配置后重试。"
  fi
}

prepare_gojudge_state_dirs() {
  resolve_gojudge_paths
  # 节点身份/结果位于状态目录（identity.json、results/），必须可写且 0700。
  # 缓存目录是另一处可写 bind，必须与 Compose 实际挂载路径一致地预先创建。
  mkdir -p "${GOJUDGE_STATE_DIR}" "${GOJUDGE_BOOTSTRAP_DIR}" "${GOJUDGE_CACHE_DIR}"
  chmod 700 "${GOJUDGE_STATE_DIR}" "${GOJUDGE_BOOTSTRAP_DIR}"
  log "判题节点状态目录：${GOJUDGE_STATE_DIR}（0700，identity.json/results 持久化）"
  log "go-judge 缓存目录：${GOJUDGE_CACHE_DIR}（可写 bind）"

  # 只读 config.yaml bind 的宿主目标是状态目录下的同名文件；必须预先以 0600 常规文件创建，
  # 否则 Docker 会把它创建成目录（OCI not a directory），导致节点容器启动失败。
  # 只在缺失时创建空占位，绝不截断既有文件或触碰 identity.json/results。
  local config_target="${GOJUDGE_STATE_DIR}/config.yaml"
  if [[ -d "${config_target}" ]]; then
    fail "配置挂载目标被占用为目录：${config_target}；请人工检查该挂载点是否被错误创建为目录或指向了错误的挂载点，\
由运维手动纠正，绝不删除同目录下的 identity.json/results。"
  fi
  if [[ ! -e "${config_target}" ]]; then
    touch "${config_target}"
    log "已预置配置挂载目标：${config_target}（空占位，0600；真实配置来自 ${GOJUDGE_CONFIG_FILE}）"
  fi
  chmod 600 "${config_target}"

  if [[ -f "${GOJUDGE_BOOTSTRAP_FILE}" ]]; then
    chmod 600 "${GOJUDGE_BOOTSTRAP_FILE}"
    log "检测到一次性 Bootstrap：${GOJUDGE_BOOTSTRAP_FILE}（0600）；服务端只原子消费 DB 中的 Bootstrap 记录，\
节点 Agent 会尝试删除本地明文并在只读挂载失败时告警，请注册完成后再由运维删除宿主机明文。"
  else
    log "未检测到 Bootstrap 文件：${GOJUDGE_BOOTSTRAP_FILE}；首次入网请由管理员签发后以 0600 放置，\
已完成注册的节点重启无需新的 Bootstrap。"
  fi
}

start_gojudge() {
  check_docker_environment
  ensure_source_ready
  resolve_gojudge_paths
  ensure_judge_security_materials
  sync_gojudge_source_code
  prepare_gojudge_config_file
  prepare_gojudge_state_dirs
  cd "${SOURCE_DIR}"
  require_file "${GOJUDGE_COMPOSE_FILE}"
  export_gojudge_variables
  gojudge_compose up -d --build
  gojudge_compose ps
  log "go-judge 沙箱仅在 hnieoj-backend 内部网络提供 http://go-judge-sandbox:5050，不发布到宿主机或公网。"
  log "判题节点配置：${GOJUDGE_CONFIG_FILE}（只读挂载到容器内 ${HNIEOJ_JUDGE_STATE_DIR:-/var/lib/hnieoj-judge-node}/config.yaml）"
  log "Bootstrap 目录：${GOJUDGE_BOOTSTRAP_DIR}（0700，只读挂载；目录内 bootstrap.token 0600，按需放置）"
}

gojudge_cache_status() {
  check_docker_environment
  resolve_gojudge_paths
  mkdir -p "${GOJUDGE_CACHE_DIR}"
  log "go-judge 缓存目录：${GOJUDGE_CACHE_DIR}"
  du -sh "${GOJUDGE_CACHE_DIR}" 2>/dev/null || true
  df -h "${GOJUDGE_CACHE_DIR}" || true
  if [[ -d "${GOJUDGE_CACHE_DIR}/problems" ]]; then
    find "${GOJUDGE_CACHE_DIR}/problems" -mindepth 1 -maxdepth 1 -type d | wc -l | awk '{print "已缓存题目数量：" $1}'
  else
    log "已缓存题目数量：0"
  fi
}

gojudge_cache_clean() {
  local keep_days="${1:-7}"
  local problem_cache_dir
  local resolved_cache_dir
  local resolved_problem_dir

  if ! [[ "${keep_days}" =~ ^[0-9]+$ ]]; then
    fail "天数必须为非负整数"
  fi
  resolve_gojudge_paths
  problem_cache_dir="${GOJUDGE_CACHE_DIR}/problems"
  mkdir -p "${problem_cache_dir}"
  resolved_cache_dir="$(realpath "${GOJUDGE_CACHE_DIR}")"
  resolved_problem_dir="$(realpath "${problem_cache_dir}")"
  if [[ "${resolved_problem_dir}" != "${resolved_cache_dir}/problems" ]]; then
    fail "缓存目录校验失败，拒绝清理：${resolved_problem_dir}"
  fi
  log "开始清理 ${keep_days} 天未修改的 go-judge 测试数据缓存：${resolved_problem_dir}"
  find "${resolved_problem_dir}" -mindepth 1 -maxdepth 1 -type d -mtime "+${keep_days}" -print -exec rm -rf {} +
  gojudge_cache_status
}


deploy_all() {
  check_runtime_environment
  sync_source_code
  prepare_environment_file
  build_project
  deploy_compose "$@"
  log "部署完成。网关地址：http://127.0.0.1:${GATEWAY_PUBLIC_PORT}"
}

build_only() {
  check_runtime_environment
  sync_source_code
  prepare_environment_file
  build_project
}

up_only() {
  check_docker_environment
  ensure_source_ready
  prepare_environment_file
  deploy_compose "$@"
}

security_init_only() {
  mkdir -p "${DEPLOY_DIR}"
  prepare_env_template_if_missing
  ensure_judge_security_materials
}

main() {
  local command="${1:-deploy}"
  if [[ "$#" -gt 0 ]]; then
    shift
  fi

  case "${command}" in
    deploy) deploy_all "$@" ;;
    pull) require_command git; sync_source_code ;;
    build) build_only ;;
    up) up_only "$@" ;;
    ps|status) show_status ;;
    logs) show_logs "$@" ;;
    restart) restart_services "$@" ;;
    stop) stop_services "$@" ;;
    down) down_services ;;
    rabbitmq-up|rabbitmq-ps|rabbitmq-logs|rabbitmq-down|judge-dlq-status|judge-dlq-requeue)
      echo "RabbitMQ 判题分发已退休：判题任务统一走共享 Redis Streams，不再提供 rabbitmq-* 或 judge-dlq-* 命令。" >&2
      exit 1
      ;;
    gojudge-up) start_gojudge ;;
    gojudge-ps) check_docker_environment; ensure_source_ready; export_gojudge_variables; gojudge_compose ps ;;
    gojudge-logs) check_docker_environment; ensure_source_ready; export_gojudge_variables; gojudge_compose logs -f --tail="${LOG_TAIL}" "$@" ;;
    gojudge-cache-status) gojudge_cache_status ;;
    gojudge-cache-clean) gojudge_cache_clean "${1:-7}" ;;
    gojudge-down) check_docker_environment; ensure_source_ready; export_gojudge_variables; gojudge_compose down ;;
    security-init) security_init_only ;;
    help|-h|--help) usage ;;
    *) usage; fail "未知命令：${command}" ;;
  esac
}

main "$@"
