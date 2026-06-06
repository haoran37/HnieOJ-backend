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
GOJUDGE_BRANCH="${GOJUDGE_BRANCH:-master}"

DEPLOY_DIR="${DEPLOY_DIR:-/opt/hnieoj/backend}"
SOURCE_DIR="${SOURCE_DIR:-${DEPLOY_DIR}/source}"
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-hnieoj-dev}"
COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-2}"
DISCARD_LOCAL_CHANGES="${DISCARD_LOCAL_CHANGES:-true}"

PROBLEM_STORAGE_DIR="${PROBLEM_STORAGE_DIR:-/data/oj/problems}"
JUDGE_SECURITY_DIR="${JUDGE_SECURITY_DIR:-/etc/hnieoj/judge-security}"
JUDGE_FORMAL_PRIVATE_KEY_PATH="${JUDGE_FORMAL_PRIVATE_KEY_PATH:-${JUDGE_SECURITY_DIR}/judge_formal_private.pem}"
JUDGE_FORMAL_PUBLIC_KEY_PATH="${JUDGE_FORMAL_PUBLIC_KEY_PATH:-${JUDGE_SECURITY_DIR}/judge_formal_public.pem}"
GOJUDGE_DEPLOY_DIR="${GOJUDGE_DEPLOY_DIR:-/opt/hnieoj/go-judge}"
GOJUDGE_SOURCE_DIR="${GOJUDGE_SOURCE_DIR:-${GOJUDGE_DEPLOY_DIR}/source}"
GOJUDGE_CONFIG_DIR="${GOJUDGE_CONFIG_DIR:-/etc/hnieoj/go-judge}"
GOJUDGE_CONFIG_FILE="${GOJUDGE_CONFIG_FILE:-${GOJUDGE_CONFIG_DIR}/config.yaml}"
GOJUDGE_CACHE_DIR="${GOJUDGE_CACHE_DIR:-/data/oj/judge-cache}"

MAVEN_SETTINGS_FILE="deploy/maven/settings.xml"
MAVEN_COMMAND="${MAVEN_COMMAND:-mvn -s ${MAVEN_SETTINGS_FILE} clean package -DskipTests}"
COMPOSE_FILE="deploy/docker/docker-compose.dev.yml"
RABBITMQ_COMPOSE_FILE="deploy/docker/docker-compose.rabbitmq.yml"
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
  rabbitmq-up         启动 RabbitMQ 容器
  rabbitmq-ps         查看 RabbitMQ 容器状态
  rabbitmq-logs       查看 RabbitMQ 容器日志
  rabbitmq-down       停止并移除 RabbitMQ 容器
  gojudge-up          拉取、构建并启动 go-judge 沙箱与判题节点
  gojudge-ps          查看 go-judge 容器状态
  gojudge-logs        查看 go-judge 日志
  gojudge-down        停止并移除 go-judge 容器
  security-init       只生成/补齐 JWT Secret 与正式节点 RSA 公私钥
  help                显示帮助

常用示例：
  bash deploy/scripts/deploy-dev.sh
  bash deploy/scripts/deploy-dev.sh logs gateway
  bash deploy/scripts/deploy-dev.sh restart hnieoj-user
  bash deploy/scripts/deploy-dev.sh rabbitmq-up
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

is_blank_or_placeholder() {
  local value="$1"
  [[ -z "${value}" || "${value}" == "replace_me" || "${value}" == *"replace_me"* ]]
}

openssl_random_urlsafe() {
  openssl rand -base64 "$1" | tr '+/' '-_' | tr -d '=\n'
}

ensure_judge_security_materials() {
  require_command openssl
  mkdir -p "${JUDGE_SECURITY_DIR}"
  chmod 700 "${JUDGE_SECURITY_DIR}"

  if [[ ! -f "${JUDGE_FORMAL_PRIVATE_KEY_PATH}" ]]; then
    log "生成正式判题节点 RSA 私钥：${JUDGE_FORMAL_PRIVATE_KEY_PATH}"
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${JUDGE_FORMAL_PRIVATE_KEY_PATH}"
    chmod 600 "${JUDGE_FORMAL_PRIVATE_KEY_PATH}"
  fi

  if [[ ! -f "${JUDGE_FORMAL_PUBLIC_KEY_PATH}" ]]; then
    log "从私钥导出正式判题节点 RSA 公钥：${JUDGE_FORMAL_PUBLIC_KEY_PATH}"
    openssl rsa -pubout -in "${JUDGE_FORMAL_PRIVATE_KEY_PATH}" -out "${JUDGE_FORMAL_PUBLIC_KEY_PATH}"
    chmod 644 "${JUDGE_FORMAL_PUBLIC_KEY_PATH}"
  fi

  if [[ -f "${ENV_FILE}" ]]; then
    local jwt_secret
    local internal_token
    jwt_secret="$(env_value HNIEOJ_JUDGE_JWT_SECRET)"
    internal_token="$(env_value HNIEOJ_INTERNAL_TOKEN)"

    if is_blank_or_placeholder "${jwt_secret}"; then
      upsert_env_value "HNIEOJ_JUDGE_JWT_SECRET" "$(openssl_random_urlsafe 64)"
      log "已写入 HNIEOJ_JUDGE_JWT_SECRET"
    fi
    if is_blank_or_placeholder "${internal_token}"; then
      upsert_env_value "HNIEOJ_INTERNAL_TOKEN" "$(openssl_random_urlsafe 48)"
      log "已写入 HNIEOJ_INTERNAL_TOKEN"
    fi
    upsert_env_value "HNIEOJ_JUDGE_SECURITY_HOST_DIR" "${JUDGE_SECURITY_DIR}"
    upsert_env_value "HNIEOJ_JUDGE_FORMAL_TOKEN_PUBLIC_KEY_PATH" "/etc/hnieoj/judge-security/$(basename "${JUDGE_FORMAL_PUBLIC_KEY_PATH}")"
    upsert_env_value "HNIEOJ_JUDGE_FORMAL_TOKEN_PRIVATE_KEY_PATH" "/etc/hnieoj/judge-security/$(basename "${JUDGE_FORMAL_PRIVATE_KEY_PATH}")"
    upsert_env_value "HNIEOJ_JUDGE_FORMAL_TOKEN_NACOS_DATA_ID" "hnieoj-judge-formal-token.yaml"
    upsert_env_value "HNIEOJ_JUDGE_FORMAL_TOKEN_NACOS_GROUP" "HNIEOJ_SECRET_GROUP"
  fi
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

  mkdir -p "${DEPLOY_DIR}" "${PROBLEM_STORAGE_DIR}" "${JUDGE_SECURITY_DIR}" "${GOJUDGE_CACHE_DIR}"
  log "目录检查通过：${PROBLEM_STORAGE_DIR}"
  log "目录检查通过：${JUDGE_SECURITY_DIR}"
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

  if grep -Eq '^(MYSQL_PASSWORD|REDIS_PASSWORD|RABBITMQ_PASSWORD)=($|replace_me)' "${ENV_FILE}"; then
    fail "${ENV_FILE} 仍包含未填写的 MySQL/Redis/RabbitMQ 密码，请填写后重试。"
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

rabbitmq_compose() {
  docker compose -p "${COMPOSE_PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${RABBITMQ_COMPOSE_FILE}" "$@"
}

gojudge_compose() {
  docker compose -p "${COMPOSE_PROJECT_NAME}" --env-file "${ENV_FILE}" -f "${GOJUDGE_COMPOSE_FILE}" "$@"
}

export_compose_variables() {
  export GATEWAY_PUBLIC_PORT GATEWAY_SERVER_PORT JAVA_BASE_IMAGE COMPOSE_PARALLEL_LIMIT
}

export_gojudge_variables() {
  export GOJUDGE_SOURCE_DIR
  export GOJUDGE_CONFIG_HOST_FILE="${GOJUDGE_CONFIG_FILE}"
  export GOJUDGE_CACHE_DIR
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

start_rabbitmq() {
  check_docker_environment
  ensure_source_ready
  cd "${SOURCE_DIR}"
  require_file "${RABBITMQ_COMPOSE_FILE}"
  rabbitmq_compose up -d
  rabbitmq_compose ps
  log "RabbitMQ AMQP 地址：127.0.0.1:${RABBITMQ_PUBLIC_PORT:-5672}"
  log "RabbitMQ 管理后台：http://127.0.0.1:${RABBITMQ_MANAGEMENT_PUBLIC_PORT:-15672}"
}

prepare_gojudge_config_file() {
  mkdir -p "${GOJUDGE_CONFIG_DIR}"
  if [[ ! -f "${GOJUDGE_CONFIG_FILE}" ]]; then
    require_file "${GOJUDGE_SOURCE_DIR}/deploy/config.formal.example.yaml"
    cp "${GOJUDGE_SOURCE_DIR}/deploy/config.formal.example.yaml" "${GOJUDGE_CONFIG_FILE}"
    chmod 600 "${GOJUDGE_CONFIG_FILE}"
    fail "已创建 ${GOJUDGE_CONFIG_FILE}，请填写 RabbitMQ 密码与 Nacos 信息后重新执行 gojudge-up。"
  fi
  require_file "${GOJUDGE_CONFIG_FILE}"
  if grep -Eq 'replace_me|password: ""' "${GOJUDGE_CONFIG_FILE}"; then
    fail "${GOJUDGE_CONFIG_FILE} 仍包含占位配置，请填写真实配置后重试。"
  fi
}

start_gojudge() {
  check_docker_environment
  ensure_source_ready
  ensure_judge_security_materials
  sync_gojudge_source_code
  prepare_gojudge_config_file
  cd "${SOURCE_DIR}"
  require_file "${GOJUDGE_COMPOSE_FILE}"
  export_gojudge_variables
  gojudge_compose up -d --build
  gojudge_compose ps
  log "go-judge 沙箱地址：http://127.0.0.1:${GOJUDGE_PUBLIC_PORT:-5050}"
  log "go-judge 判题节点配置：${GOJUDGE_CONFIG_FILE}"
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
    rabbitmq-up) start_rabbitmq ;;
    rabbitmq-ps) check_docker_environment; ensure_source_ready; rabbitmq_compose ps ;;
    rabbitmq-logs) check_docker_environment; ensure_source_ready; rabbitmq_compose logs -f --tail="${LOG_TAIL}" rabbitmq ;;
    rabbitmq-down) check_docker_environment; ensure_source_ready; rabbitmq_compose down ;;
    gojudge-up) start_gojudge ;;
    gojudge-ps) check_docker_environment; ensure_source_ready; export_gojudge_variables; gojudge_compose ps ;;
    gojudge-logs) check_docker_environment; ensure_source_ready; export_gojudge_variables; gojudge_compose logs -f --tail="${LOG_TAIL}" "$@" ;;
    gojudge-down) check_docker_environment; ensure_source_ready; export_gojudge_variables; gojudge_compose down ;;
    security-init) security_init_only ;;
    help|-h|--help) usage ;;
    *) usage; fail "未知命令：${command}" ;;
  esac
}

main "$@"
