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

DEPLOY_DIR="${DEPLOY_DIR:-/opt/hnieoj/backend}"
SOURCE_DIR="${SOURCE_DIR:-${DEPLOY_DIR}/source}"
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-hnieoj-dev}"
COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-2}"
DISCARD_LOCAL_CHANGES="${DISCARD_LOCAL_CHANGES:-true}"

PROBLEM_STORAGE_DIR="${PROBLEM_STORAGE_DIR:-/data/oj/problems}"
JUDGE_SECURITY_DIR="${JUDGE_SECURITY_DIR:-/etc/hnieoj/judge-security}"

MAVEN_SETTINGS_FILE="deploy/maven/settings.xml"
MAVEN_COMMAND="${MAVEN_COMMAND:-mvn -s ${MAVEN_SETTINGS_FILE} clean package -DskipTests}"
COMPOSE_FILE="deploy/docker/docker-compose.dev.yml"
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
  deploy          拉取代码、打包并重新部署，默认命令
  pull            只拉取 ${DEPLOY_BRANCH} 最新代码
  build           拉取代码并执行 Maven 打包
  up [服务名...]  使用 Docker Compose 构建并启动服务
  ps              查看容器状态
  logs [服务名]   查看日志，默认跟随全部服务日志
  restart [服务名...] 重启服务，不传服务名则重启全部服务
  stop [服务名...]    停止服务，不传服务名则停止全部服务
  down            停止并移除 Compose 容器
  help            显示帮助

常用示例：
  bash deploy/scripts/deploy-dev.sh
  bash deploy/scripts/deploy-dev.sh ps
  bash deploy/scripts/deploy-dev.sh logs gateway
  bash deploy/scripts/deploy-dev.sh restart hnieoj-user
EOF
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令：$1"
  log "命令检查通过：$1"
}

require_dir() {
  [[ -d "$1" ]] || fail "缺少目录：$1"
  log "目录检查通过：$1"
}

require_file() {
  [[ -f "$1" ]] || fail "缺少文件：$1"
  log "文件检查通过：$1"
}

setup_git_auth() {
  if [[ "${GIT_REPO_URL}" != http* ]]; then
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

  if ! grep -q "^${key}=" "${ENV_FILE}"; then
    printf '%s=%s\n' "${key}" "${value}" >> "${ENV_FILE}"
  else
    sed -i "s|^${key}=.*|${key}=${value}|" "${ENV_FILE}"
  fi
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

  docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 不可用"
  docker info >/dev/null 2>&1 || fail "当前用户无法访问 Docker daemon"

  mkdir -p "${DEPLOY_DIR}" || fail "无法创建部署目录：${DEPLOY_DIR}"
  require_dir "${PROBLEM_STORAGE_DIR}"
  require_dir "${JUDGE_SECURITY_DIR}"
}

check_docker_environment() {
  require_command docker
  docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 不可用"
  docker info >/dev/null 2>&1 || fail "当前用户无法访问 Docker daemon"
}

sync_source_code() {
  log "开始同步源码"
  setup_git_auth

  if [[ ! -d "${SOURCE_DIR}/.git" ]]; then
    if [[ -d "${SOURCE_DIR}" && -n "$(find "${SOURCE_DIR}" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
      fail "源码目录不是 Git 仓库且非空：${SOURCE_DIR}。请备份后清空该目录，或修改 SOURCE_DIR。"
    fi
    mkdir -p "$(dirname "${SOURCE_DIR}")"
    git clone --branch "${DEPLOY_BRANCH}" --single-branch "${GIT_REPO_URL}" "${SOURCE_DIR}"
  fi

  cd "${SOURCE_DIR}"

  if [[ "${DISCARD_LOCAL_CHANGES}" == "true" ]]; then
    log "部署目录只作为运行环境使用，将丢弃服务器本地源码改动。"
    git reset --hard
    git clean -fd
  elif ! git diff --quiet || ! git diff --cached --quiet || [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
    fail "源码目录存在本地改动：${SOURCE_DIR}。如确认丢弃，请使用 DISCARD_LOCAL_CHANGES=true。"
  fi

  git remote set-url origin "${GIT_REPO_URL}"
  git fetch --prune origin "${DEPLOY_BRANCH}"
  git checkout "${DEPLOY_BRANCH}"
  git reset --hard "origin/${DEPLOY_BRANCH}"

  log "源码版本：$(git rev-parse --short HEAD)"
}

ensure_source_ready() {
  [[ -d "${SOURCE_DIR}/.git" ]] || fail "源码目录不存在，请先执行：bash deploy/scripts/deploy-dev.sh deploy"
  cd "${SOURCE_DIR}"
  require_file "${COMPOSE_FILE}"
  require_file "${ENV_FILE}"
}

prepare_environment_file() {
  log "检查部署环境变量文件"
  cd "${SOURCE_DIR}"
  require_file "${ENV_TEMPLATE_FILE}"

  if [[ ! -f "${ENV_FILE}" ]]; then
    cp "${ENV_TEMPLATE_FILE}" "${ENV_FILE}"
    chmod 600 "${ENV_FILE}"
    upsert_env_value "GATEWAY_PUBLIC_PORT" "${GATEWAY_PUBLIC_PORT}"
    upsert_env_value "GATEWAY_SERVER_PORT" "${GATEWAY_SERVER_PORT}"
    upsert_env_value "JAVA_BASE_IMAGE" "${JAVA_BASE_IMAGE}"
    fail "已创建 ${ENV_FILE}。请填写真实 MySQL/Redis/RabbitMQ/Nacos/安全配置后重新执行脚本。"
  fi

  require_file "${ENV_FILE}"
  upsert_env_value "GATEWAY_PUBLIC_PORT" "${GATEWAY_PUBLIC_PORT}"
  upsert_env_value "GATEWAY_SERVER_PORT" "${GATEWAY_SERVER_PORT}"
  upsert_env_value "JAVA_BASE_IMAGE" "${JAVA_BASE_IMAGE}"

  if grep -Eq '=(replace_me|)$' "${ENV_FILE}"; then
    log "警告：${ENV_FILE} 仍包含空值或 replace_me，占位配置未填写时 Compose 可能失败。"
  fi
}

build_project() {
  log "开始 Maven 打包"
  cd "${SOURCE_DIR}"
  require_file "${MAVEN_SETTINGS_FILE}"
  ${MAVEN_COMMAND}
}

compose() {
  docker compose \
    -p "${COMPOSE_PROJECT_NAME}" \
    --env-file "${ENV_FILE}" \
    -f "${COMPOSE_FILE}" \
    "$@"
}

export_compose_variables() {
  export GATEWAY_PUBLIC_PORT
  export GATEWAY_SERVER_PORT
  export JAVA_BASE_IMAGE
  export COMPOSE_PARALLEL_LIMIT
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
  if [[ "$#" -eq 0 ]]; then
    compose logs -f --tail="${LOG_TAIL}"
  else
    compose logs -f --tail="${LOG_TAIL}" "$@"
  fi
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
  if [[ "$#" -eq 0 ]]; then
    compose stop
  else
    compose stop "$@"
  fi
}

down_services() {
  check_docker_environment
  ensure_source_ready
  export_compose_variables
  compose down
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

main() {
  local command="${1:-deploy}"
  if [[ "$#" -gt 0 ]]; then
    shift
  fi

  case "${command}" in
    deploy)
      deploy_all "$@"
      ;;
    pull)
      require_command git
      sync_source_code
      ;;
    build)
      build_only
      ;;
    up)
      up_only "$@"
      ;;
    ps|status)
      show_status
      ;;
    logs)
      show_logs "$@"
      ;;
    restart)
      restart_services "$@"
      ;;
    stop)
      stop_services "$@"
      ;;
    down)
      down_services
      ;;
    help|-h|--help)
      usage
      ;;
    *)
      usage
      fail "未知命令：${command}"
      ;;
  esac
}

main "$@"
