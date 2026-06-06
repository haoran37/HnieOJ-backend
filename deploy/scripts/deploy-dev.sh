#!/usr/bin/env bash

set -Eeuo pipefail

#######################################
# 全局部署变量。
# 如需调整分支、端口或目录，优先修改这里。
#######################################
DEPLOY_BRANCH="${DEPLOY_BRANCH:-dev}"
GATEWAY_PUBLIC_PORT="${GATEWAY_PUBLIC_PORT:-8800}"
GATEWAY_SERVER_PORT="${GATEWAY_SERVER_PORT:-8800}"

GIT_REPO_URL="${GIT_REPO_URL:-git@github.com:haoran37/HnieOJ-backend.git}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/hnieoj/backend}"
SOURCE_DIR="${SOURCE_DIR:-${DEPLOY_DIR}/source}"
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-hnieoj-dev}"

PROBLEM_STORAGE_DIR="${PROBLEM_STORAGE_DIR:-/data/oj/problems}"
JUDGE_SECURITY_DIR="${JUDGE_SECURITY_DIR:-/etc/hnieoj/judge-security}"

MAVEN_SETTINGS_FILE="deploy/maven/settings.xml"
COMPOSE_FILE="deploy/docker/docker-compose.dev.yml"
ENV_TEMPLATE_FILE="deploy/docker/.env.example"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
  printf '错误：%s\n' "$*" >&2
  exit 1
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
  log "网关端口映射：${GATEWAY_PUBLIC_PORT}:${GATEWAY_SERVER_PORT}"
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

sync_source_code() {
  log "开始同步源码"

  if [[ ! -d "${SOURCE_DIR}/.git" ]]; then
    mkdir -p "$(dirname "${SOURCE_DIR}")"
    git clone --branch "${DEPLOY_BRANCH}" --single-branch "${GIT_REPO_URL}" "${SOURCE_DIR}"
  fi

  cd "${SOURCE_DIR}"

  if ! git diff --quiet || ! git diff --cached --quiet; then
    fail "源码目录存在本地未提交变更：${SOURCE_DIR}"
  fi

  git remote set-url origin "${GIT_REPO_URL}"
  git fetch --prune origin "${DEPLOY_BRANCH}"
  git checkout "${DEPLOY_BRANCH}"
  git reset --hard "origin/${DEPLOY_BRANCH}"

  log "源码版本：$(git rev-parse --short HEAD)"
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
    fail "已创建 ${ENV_FILE}。请填写真实 MySQL/Redis/RabbitMQ/Nacos/安全配置后重新执行脚本。"
  fi

  require_file "${ENV_FILE}"
  upsert_env_value "GATEWAY_PUBLIC_PORT" "${GATEWAY_PUBLIC_PORT}"
  upsert_env_value "GATEWAY_SERVER_PORT" "${GATEWAY_SERVER_PORT}"

  if grep -Eq '=(replace_me|)$' "${ENV_FILE}"; then
    log "警告：${ENV_FILE} 仍包含空值或 replace_me，占位配置未填写时 Compose 可能失败。"
  fi
}

build_project() {
  log "开始 Maven 打包"
  cd "${SOURCE_DIR}"
  require_file "${MAVEN_SETTINGS_FILE}"
  mvn -s "${MAVEN_SETTINGS_FILE}" clean package -DskipTests
}

deploy_compose() {
  log "开始部署 Docker Compose 服务"
  cd "${SOURCE_DIR}"
  require_file "${COMPOSE_FILE}"

  export GATEWAY_PUBLIC_PORT
  export GATEWAY_SERVER_PORT

  docker compose \
    -p "${COMPOSE_PROJECT_NAME}" \
    --env-file "${ENV_FILE}" \
    -f "${COMPOSE_FILE}" \
    up -d --build --remove-orphans

  docker compose \
    -p "${COMPOSE_PROJECT_NAME}" \
    --env-file "${ENV_FILE}" \
    -f "${COMPOSE_FILE}" \
    ps
}

main() {
  check_runtime_environment
  sync_source_code
  prepare_environment_file
  build_project
  deploy_compose
  log "部署完成。网关地址：http://127.0.0.1:${GATEWAY_PUBLIC_PORT}"
}

main "$@"
