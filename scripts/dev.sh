#!/bin/sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

fail() {
  printf '启动失败：%s\n' "$1" >&2
  exit 1
}

port_owner() {
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -t -iTCP:"$1" -sTCP:LISTEN 2>/dev/null | paste -sd, - || true
  fi
}

api_responds() {
  health=$(curl -sS --max-time 3 "$1/api/health" 2>/dev/null || true)
  login_status=$(curl -sS --max-time 3 -o /dev/null -w '%{http_code}' \
    -H 'Content-Type: application/json' --data '{}' \
    "$1/api/auth/login" 2>/dev/null || true)
  case "$health" in
    *'"application":"UP"'*'"database":"UP"'*) [ "$login_status" = 400 ] ;;
    *) return 1 ;;
  esac
}

case "${1:-}" in
  backend)
    env_file=${ENV_FILE:-"$project_root/backend/.env"}
    case "$env_file" in
      /*) ;;
      *) env_file="$PWD/$env_file" ;;
    esac
    [ -f "$env_file" ] || fail "缺少 ${env_file}；先按 README 配置后端环境文件。"
    set -a
    . "$env_file"
    set +a
    [ -n "${DB_PASSWORD:-}" ] || fail 'DB_PASSWORD 未配置。'
    [ -n "${JWT_SECRET:-}" ] || fail 'JWT_SECRET 未配置。'
    port=${SERVER_PORT:-8080}
    case "$port" in *[!0-9]*|'') fail "SERVER_PORT 必须是数字。" ;; esac
    owner=$(port_owner "$port")
    if [ -n "$owner" ]; then
      if api_responds "http://127.0.0.1:$port"; then
        printf '后端已在端口 %s 运行（PID %s），无需重复启动；若刚修改 %s，请先停止旧进程。\n' "$port" "$owner" "$env_file"
        exit 0
      fi
      fail "端口 $port 被 PID $owner 占用，但健康检查或登录校验失败；请停止旧后端后重试。"
    fi
    printf '使用 %s 启动后端，端口 %s。\n' "$env_file" "$port"
    cd "$project_root/backend"
    exec ./mvnw spring-boot:run
    ;;
  frontend)
    owner=$(port_owner 5173)
    if [ -n "$owner" ]; then
      if curl -fsS --max-time 3 http://127.0.0.1:5173/login >/dev/null 2>&1 \
        && api_responds http://127.0.0.1:5173; then
        printf '前端已在 http://127.0.0.1:5173/login 运行（PID %s），无需重复启动；若刚修改代理配置，请先停止旧进程。\n' "$owner"
        exit 0
      fi
      fail "端口 5173 被 PID $owner 占用，但页面或后端代理不可用；请检查后端并停止旧前端后重试。"
    fi
    [ -x "$project_root/frontend/node_modules/.bin/vite" ] \
      || fail '前端依赖不存在；先在 frontend/ 运行 npm ci。'
    cd "$project_root/frontend"
    exec npm run dev
    ;;
  *)
    printf '用法：%s backend|frontend\n' "$0" >&2
    exit 2
    ;;
esac
