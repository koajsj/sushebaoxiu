#!/bin/bash
set -euo pipefail
umask 077

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
run_dir="$root/.run"
log_dir="$root/logs"
mode=${1:-}
phase=准备
lock_held=0
new_backend=0
new_frontend=0
mkdir -p "$run_dir" "$log_dir"

say() { printf '%s\n' "$*"; }
fail() { printf '✗ [%s] %s\n' "$phase" "$*" >&2; exit 1; }

log_tail() {
  local file=$1 causes errors
  [ -s "$file" ] || return 0
  printf '相关日志：%s\n' "$file" >&2
  causes=$(grep -E 'Caused by:' "$file" | tail -n 3 || true)
  errors=$(grep -Ei 'ERROR|FAILURE|Access denied|Unknown database|refused|EADDRINUSE' "$file" | tail -n 3 || true)
  if [ -n "$causes$errors" ]; then
    [ -z "$errors" ] || printf '%s\n' "$errors" >&2
    [ -z "$causes" ] || printf '%s\n' "$causes" >&2
  else
    tail -n 12 "$file" >&2
  fi
}

listen_pid() {
  lsof -nP -t -iTCP:"$1" -sTCP:LISTEN 2>/dev/null | sort -u | head -n 1 || true
}

owned_pid() {
  local kind=$1 file="$run_dir/$1.pid" pid stamp actual command expected
  [ -f "$file" ] || return 1
  IFS='|' read -r pid stamp < "$file" || return 1
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  actual=$(ps -p "$pid" -o lstart= 2>/dev/null | sed 's/^ *//; s/ *$//')
  [ -n "$actual" ] && [ "$actual" = "$stamp" ] || return 1
  command=$(ps -ww -p "$pid" -o command= 2>/dev/null || true)
  if [ "$kind" = backend ]; then
    expected="$root/backend/target/campus-repair-0.0.1-SNAPSHOT.jar"
  else
    expected="$root/frontend/node_modules/vite/bin/vite.js"
  fi
  [[ "$command" == *"$expected"* ]] || return 1
  printf '%s\n' "$pid"
}

remember_pid() {
  local pid=$2 stamp attempts=0
  while [ "$attempts" -lt 10 ]; do
    stamp=$(ps -p "$pid" -o lstart= 2>/dev/null | sed 's/^ *//; s/ *$//')
    if [ -n "$stamp" ]; then
      printf '%s|%s\n' "$pid" "$stamp" > "$run_dir/$1.pid"
      return 0
    fi
    attempts=$((attempts + 1))
    sleep 0.1
  done
  return 1
}

stop_managed() {
  local kind=$1 pid file="$run_dir/$1.pid" tries=0
  if ! pid=$(owned_pid "$kind"); then
    [ ! -f "$file" ] || { rm -f "$file"; say "• $kind 的 PID 记录已失效，未结束任何进程。"; }
    return 0
  fi
  # Verify immediately before signaling; never kill a PID based on a stale file alone.
  [ "$(owned_pid "$kind" || true)" = "$pid" ] || return 1
  kill "$pid" 2>/dev/null || return 1
  while [ "$tries" -lt 20 ] && kill -0 "$pid" 2>/dev/null; do
    sleep 0.5
    tries=$((tries + 1))
  done
  if kill -0 "$pid" 2>/dev/null; then
    say "✗ $kind (PID $pid) 未在 10 秒内退出；请检查 $log_dir/$kind.log。"
    return 1
  fi
  rm -f "$file"
  say "✓ 已停止 $kind (PID $pid)"
}

finish() {
  local code=$?
  trap - EXIT
  if [ "$code" -ne 0 ] && [ "$mode" = start ]; then
    [ "$new_frontend" -eq 0 ] || stop_managed frontend || true
    [ "$new_backend" -eq 0 ] || stop_managed backend || true
  fi
  release_lock
  exit "$code"
}
trap finish EXIT

release_lock() {
  if [ "$lock_held" -eq 1 ] && [ "$(cut -d '|' -f 1 "$run_dir/lock/pid" 2>/dev/null || true)" = "$$" ]; then
    rm -f "$run_dir/lock/pid"
    rmdir "$run_dir/lock" 2>/dev/null || true
  fi
  lock_held=0
}

acquire_lock() {
  local previous stamp actual command
  if ! mkdir "$run_dir/lock" 2>/dev/null; then
    IFS='|' read -r previous stamp < "$run_dir/lock/pid" 2>/dev/null || true
    if [[ "${previous:-}" =~ ^[0-9]+$ ]]; then
      actual=$(ps -p "$previous" -o lstart= 2>/dev/null | sed 's/^ *//; s/ *$//')
      command=$(ps -ww -p "$previous" -o command= 2>/dev/null || true)
      if [ -n "$actual" ] && [ "$actual" = "${stamp:-}" ] \
        && [[ "$command" == *"$root/scripts/local-runtime.sh"* ]]; then
        fail "另一个启动或停止操作正在运行 (PID $previous)。"
      fi
    fi
    rm -f "$run_dir/lock/pid"
    rmdir "$run_dir/lock" 2>/dev/null || fail '无法清理旧锁，请检查 .run/lock。'
    mkdir "$run_dir/lock" || fail '无法获取启动锁。'
  fi
  printf '%s|%s\n' "$$" "$(ps -p "$$" -o lstart= | sed 's/^ *//; s/ *$//')" > "$run_dir/lock/pid"
  lock_held=1
}

mysql_app() {
  MYSQL_PWD="$DB_PASSWORD" "$mysql_cli" --protocol=tcp --host="$DB_HOST" \
    --port="$DB_PORT" --user="$DB_USERNAME" --connect-timeout=3 "$@"
}

mysql_root() {
  "$mysql_cli" --defaults-extra-file="$admin_file" --protocol=socket "$@"
}

mysql_up() {
  "$mysql_admin" --protocol=tcp --host=127.0.0.1 --port="$DB_PORT" \
    --connect-timeout=2 ping >/dev/null 2>&1
}

wait_mysql() {
  local tries=0
  while [ "$tries" -lt 30 ]; do
    mysql_up && return 0
    sleep 1
    tries=$((tries + 1))
  done
  return 1
}

load_local_config() {
  if [ -n "${LOCAL_ENV_FILE:-}" ]; then
    config=$LOCAL_ENV_FILE
  elif [ -f "$root/backend/.env.local" ]; then
    config="$root/backend/.env.local"
  else
    config="$root/.runtime/backend.env"
  fi
  case "$config" in /*) ;; *) config="$root/$config" ;; esac
  [ -f "$config" ] || fail '缺少本机配置；复制 backend/.env.local.example 为 backend/.env.local 并填写密码/密钥。'
  set -a
  # shellcheck disable=SC1090
  . "$config"
  set +a
  DB_HOST=${DB_HOST:-127.0.0.1}
  DB_PORT=${DB_PORT:-13306}
  DB_NAME=${DB_NAME:-campus_repair}
  DB_USERNAME=${DB_USERNAME:-campus_repair}
  SERVER_PORT=${SERVER_PORT:-8080}
  [ "$DB_HOST" = 127.0.0.1 ] || fail 'DB_HOST 必须为 127.0.0.1。'
  [ "${SERVER_ADDRESS:-127.0.0.1}" = 127.0.0.1 ] || fail 'SERVER_ADDRESS 必须为 127.0.0.1。'
  [ "$DB_NAME" = campus_repair ] || fail '仅允许启动项目专用 campus_repair 数据库。'
  [[ "$DB_PORT" =~ ^[0-9]+$ && "$SERVER_PORT" =~ ^[0-9]+$ ]] || fail '数据库或后端端口不是有效数字。'
  [ -n "${DB_PASSWORD:-}" ] && [ -n "${JWT_SECRET:-}" ] || fail 'DB_PASSWORD 或 JWT_SECRET 未配置。'
  if [ -n "${JAVA_HOME:-}" ]; then
    [ -x "$JAVA_HOME/bin/java" ] || fail 'JAVA_HOME 指向的 Java 不存在。'
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH
  fi
}

check_tools() {
  local java_version java_major node_version node_major node_minor
  [ "$(uname -s)" = Darwin ] || fail '一键启动仅用于本机 macOS。'
  for tool in java node npm curl lsof ps open; do
    command -v "$tool" >/dev/null 2>&1 || fail "缺少 $tool；请先安装或修复本机环境。"
  done
  [ -x "$root/backend/mvnw" ] || fail '缺少可执行的 backend/mvnw。'
  java_version=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')
  java_major=${java_version%%.*}
  [[ "$java_major" =~ ^[0-9]+$ ]] && [ "$java_major" -ge 17 ] || fail "需要 Java 17+，当前为 $java_version。"
  node_version=$(node -p 'process.versions.node')
  node_major=${node_version%%.*}
  node_minor=${node_version#*.}; node_minor=${node_minor%%.*}
  if ! { [ "$node_major" -eq 20 ] && [ "$node_minor" -ge 19 ]; } \
    && ! { [ "$node_major" -eq 22 ] && [ "$node_minor" -ge 12 ]; } \
    && [ "$node_major" -lt 23 ]; then
    fail "Node $node_version 不符合前端要求（20.19+ 或 22.12+）。"
  fi
  if command -v mysql >/dev/null 2>&1; then
    mysql_cli=$(command -v mysql)
  elif [ -x /usr/local/mysql/bin/mysql ]; then
    mysql_cli=/usr/local/mysql/bin/mysql
  elif [ -x "$HOME/.cache/campus-repair/tools/mysql-8.4.9-macos15-arm64/bin/mysql" ]; then
    mysql_cli="$HOME/.cache/campus-repair/tools/mysql-8.4.9-macos15-arm64/bin/mysql"
  else
    fail '未找到 MySQL 客户端；检查本机 MySQL 安装。'
  fi
  mysql_admin="$(dirname "$mysql_cli")/mysqladmin"
  [ -x "$mysql_admin" ] || fail '未找到 mysqladmin，无法检查 MySQL 状态。'
  admin_file=${MYSQL_ADMIN_DEFAULTS_FILE:-"$root/.runtime/mysql-root.cnf"}
  say "✓ Java $java_version · Node $node_version · Maven Wrapper · MySQL 客户端"
}

check_database() {
  local exists tables columns admin_port version error_file="$log_dir/database-check.log" script
  if ! mysql_up; then
    say "• MySQL 127.0.0.1:$DB_PORT 未运行，尝试启动本机服务…"
    if [ "$DB_PORT" = 13306 ] && [ -x "$root/.runtime/start-mysql.sh" ]; then
      "$root/.runtime/start-mysql.sh" > "$log_dir/mysql-start.log" 2>&1 \
        || { log_tail "$log_dir/mysql-start.log"; fail '本机项目专用 MySQL 启动失败。'; }
    elif [ "$DB_PORT" = 3306 ] && command -v brew >/dev/null 2>&1; then
      local service
      service=$(brew services list 2>/dev/null | awk '$1 ~ /^mysql(@[0-9.]+)?$/ && $2 == "stopped" {print $1; exit}')
      [ -n "$service" ] || fail '3306 未运行，也未找到已安装且停止的 Homebrew MySQL 服务；请检查本机安装方式。'
      brew services start "$service" > "$log_dir/mysql-start.log" 2>&1 \
        || { log_tail "$log_dir/mysql-start.log"; fail "无法启动 Homebrew 服务 $service。"; }
    else
      fail "端口 $DB_PORT 无 MySQL 服务；请检查配置及实际安装方式。"
    fi
    wait_mysql || { log_tail "$log_dir/mysql-start.log"; fail 'MySQL 已尝试启动，但目标端口仍不可连接。'; }
  fi
  say "✓ MySQL 127.0.0.1:$DB_PORT 已运行"

  phase='3/6 检查数据库'
  say "[$phase]"

  if ! exists=$(mysql_app -N -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='campus_repair'" 2> "$error_file"); then
    log_tail "$error_file"
    fail '数据库账号连接失败；检查本机配置中的端口、用户名和密码。'
  fi
  version=$(mysql_app -N -e 'SELECT VERSION()' 2> "$error_file") \
    || { log_tail "$error_file"; fail '无法读取 MySQL 版本。'; }
  [[ "$version" == 8.* ]] || fail "当前 MySQL 为 $version，本项目需要 MySQL 8。"
  if [ "$exists" = 0 ]; then
    [ -f "$admin_file" ] || fail '数据库不存在，且没有项目专用 MySQL 管理配置；不会改动其他实例。'
    admin_port=$(mysql_root -N -e 'SELECT @@port' 2> "$error_file") \
      || { log_tail "$error_file"; fail '无法验证 MySQL 管理配置所指向的实例。'; }
    [ "$admin_port" = "$DB_PORT" ] || fail "MySQL 管理配置指向端口 $admin_port，与应用端口 $DB_PORT 不一致；拒绝初始化。"
    say '• 仅创建新的 campus_repair 库并按现有顺序初始化…'
    : > "$log_dir/database-init.log"
    for script in init phase3 phase4 phase5 business-enhancements notification-after-commit final-hardening dev-users dev-business dev-dispatch; do
      mysql_root < "$root/sql/$script.sql" >> "$log_dir/database-init.log" 2>&1 \
        || { log_tail "$log_dir/database-init.log"; fail "初始化 sql/$script.sql 失败，请检查日志；不会自动重建现有库。"; }
    done
  fi
  if ! tables=$(mysql_app -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='campus_repair'" 2> "$error_file"); then
    log_tail "$error_file"
    fail '无法读取 campus_repair 表结构。'
  fi
  columns=$(mysql_app -N -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='campus_repair' AND ((TABLE_NAME='repair_order' AND COLUMN_NAME IN ('start_due_time','request_key','request_hash')) OR (TABLE_NAME='repair_record' AND COLUMN_NAME IN ('request_key','request_hash')))" 2> "$error_file") \
    || { log_tail "$error_file"; fail '无法核对数据库迁移状态。'; }
  [ "$tables" -ge 13 ] && [ "$columns" -eq 5 ] \
    || fail "数据库结构不完整（表 $tables/13，关键列 $columns/5）；请先备份并按 README 的迁移顺序处理，启动器不会覆盖现有数据。"
  say "✓ campus_repair 数据库与关键迁移结构可用"
}

on_signal() {
  trap - INT TERM HUP
  say '• 启动窗口收到退出信号，正在停止由它启动的服务…'
  [ "$new_frontend" -eq 0 ] || stop_managed frontend || true
  [ "$new_backend" -eq 0 ] || stop_managed backend || true
  exit 0
}

monitor_services() {
  local backend_pid frontend_pid
  trap on_signal INT TERM HUP
  say '• 启动窗口保持打开；按 Ctrl+C 可停止本次启动的服务。'
  while :; do
    backend_pid=$(owned_pid backend || true)
    frontend_pid=$(owned_pid frontend || true)
    if [ -z "$backend_pid" ] || [ -z "$frontend_pid" ]; then
      say '• 服务已停止，启动窗口即将退出。'
      return 0
    fi
    sleep 3
  done
}

backend_ready() {
  local health status
  health=$(curl -fsS --max-time 2 "http://127.0.0.1:$SERVER_PORT/api/health" 2>/dev/null || true)
  status=$(curl -sS --max-time 2 -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
    --data '{}' "http://127.0.0.1:$SERVER_PORT/api/auth/login" 2>/dev/null || true)
  [[ "$health" == *'"application":"UP"'*'"database":"UP"'* ]] && [ "$status" = 400 ]
}

frontend_ready() {
  local status
  status=$(curl -sS --max-time 2 -o /dev/null -w '%{http_code}' http://127.0.0.1:5173/login 2>/dev/null || true)
  [ "$status" = 200 ] && curl -fsS --max-time 2 http://127.0.0.1:5173/api/health 2>/dev/null \
    | grep -q '"database":"UP"'
}

wait_service() {
  local kind=$1 limit=$2 pid=$3 tries=0
  while [ "$tries" -lt "$limit" ]; do
    if "${kind}_ready"; then return 0; fi
    kill -0 "$pid" 2>/dev/null || return 1
    sleep 1
    tries=$((tries + 1))
  done
  return 1
}

stray_project_jar_pid() {
  local pid command cwd jar="$root/backend/target/campus-repair-0.0.1-SNAPSHOT.jar"
  for pid in $(pgrep -f 'campus-repair-0.0.1-SNAPSHOT.jar' 2>/dev/null || true); do
    command=$(ps -ww -p "$pid" -o command= 2>/dev/null || true)
    cwd=$(lsof -a -p "$pid" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | tail -n 1 || true)
    if [[ "$command" == *'java -jar '* ]] \
      && { [[ "$command" == *"$jar"* ]] || [ "$cwd" = "$root/backend" ]; }; then
      printf '%s\n' "$pid"
      return 0
    fi
  done
  return 1
}

start_backend() {
  local owner managed stray jar="$root/backend/target/campus-repair-0.0.1-SNAPSHOT.jar" pid
  owner=$(listen_pid "$SERVER_PORT")
  managed=$(owned_pid backend || true)
  if [ -n "$owner" ]; then
    [ "$owner" = "$managed" ] || fail "端口 $SERVER_PORT 已被非本次启动器管理的 PID $owner 占用；请核对后自行停止。"
    backend_ready || fail '已有后端进程未通过健康和登录校验；请运行 stop-local.command 后重试。'
    say "✓ 已有本项目后端可用 (PID $owner)"
    return 0
  fi
  [ -z "$managed" ] || fail '后端 PID 仍存在但未监听，先运行 stop-local.command。'
  stray=$(stray_project_jar_pid || true)
  [ -z "$stray" ] || fail "发现旧的本项目 JAR 进程 PID $stray 未监听端口；请核对并正常停止后再构建，以免覆盖运行中的 JAR。"
  rm -f "$run_dir/backend.pid"
  say '• 正在编译后端，普通 Maven 输出写入 logs/backend-build.log…'
  (cd "$root/backend" && ./mvnw -q -DskipTests package) > "$log_dir/backend-build.log" 2>&1 \
    || { log_tail "$log_dir/backend-build.log"; fail '后端构建失败。'; }
  [ -f "$jar" ] || fail '构建完成但找不到后端 JAR。'
  (
    cd "$root/backend"
    SPRING_PROFILES_ACTIVE=local LOG_FILE="$log_dir/backend-app.log" \
      nohup java -jar "$jar" > "$log_dir/backend.log" 2>&1 &
    echo $! > "$run_dir/backend.new"
  )
  pid=$(cat "$run_dir/backend.new"); rm -f "$run_dir/backend.new"
  remember_pid backend "$pid" || { log_tail "$log_dir/backend.log"; fail '后端启动进程很快退出。'; }
  new_backend=1
  wait_service backend 75 "$pid" || { log_tail "$log_dir/backend.log"; fail '后端未在 75 秒内通过数据库健康及登录接口检查。'; }
  say "✓ Backend ready (PID $pid)"
}

start_frontend() {
  local owner managed pid
  owner=$(listen_pid 5173)
  managed=$(owned_pid frontend || true)
  if [ -n "$owner" ]; then
    [ "$owner" = "$managed" ] || fail "端口 5173 已被非本次启动器管理的 PID $owner 占用；请核对后自行停止。"
    frontend_ready || fail '已有前端未通过页面或 API 代理检查；请运行 stop-local.command 后重试。'
    say "✓ 已有本项目前端可用 (PID $owner)"
    return 0
  fi
  [ -z "$managed" ] || fail '前端 PID 仍存在但未监听，先运行 stop-local.command。'
  rm -f "$run_dir/frontend.pid"
  if [ ! -f "$root/frontend/node_modules/vite/bin/vite.js" ]; then
    say '• 首次安装前端已有依赖，输出写入 logs/frontend-install.log…'
    (cd "$root/frontend" && npm ci) > "$log_dir/frontend-install.log" 2>&1 \
      || { log_tail "$log_dir/frontend-install.log"; fail 'npm ci 失败。'; }
  fi
  (
    cd "$root/frontend"
    BACKEND_PROXY_TARGET="http://127.0.0.1:$SERVER_PORT" \
      nohup node "$root/frontend/node_modules/vite/bin/vite.js" --host 127.0.0.1 --port 5173 --strictPort \
      > "$log_dir/frontend.log" 2>&1 &
    echo $! > "$run_dir/frontend.new"
  )
  pid=$(cat "$run_dir/frontend.new"); rm -f "$run_dir/frontend.new"
  remember_pid frontend "$pid" || { log_tail "$log_dir/frontend.log"; fail '前端启动进程很快退出。'; }
  new_frontend=1
  wait_service frontend 30 "$pid" || { log_tail "$log_dir/frontend.log"; fail '前端未在 30 秒内通过页面及 API 代理检查。'; }
  say "✓ Frontend ready (PID $pid)"
}

case "$mode" in
  start)
    acquire_lock
    phase='1/6 检查运行环境'
    say "[$phase]"
    load_local_config
    check_tools
    phase='2/6 检查 MySQL'
    say "[$phase]"
    check_database
    phase='4/6 启动 Spring Boot'
    say "[$phase]"
    start_backend
    phase='5/6 启动 Vue'
    say "[$phase]"
    start_frontend
    phase='6/6 打开系统'
    say "[$phase] http://127.0.0.1:5173/login"
    if [ "${LOCAL_NO_OPEN:-0}" != 1 ]; then
      open http://127.0.0.1:5173/login || fail '服务已就绪，但浏览器未能自动打开；请手动访问上述地址。'
    fi
    say '✓ 本机系统已就绪；双击 stop-local.command 可停止本启动器管理的前后端。'
    release_lock
    if [ "$new_backend" -eq 1 ] || [ "$new_frontend" -eq 1 ]; then
      monitor_services
    fi
    ;;
  stop)
    phase=停止本机服务
    acquire_lock
    stop_managed frontend || fail '前端未正常退出。'
    stop_managed backend || fail '后端未正常退出。'
    say '✓ 已检查本启动器管理的前后端；MySQL 保持运行。'
    ;;
  *) printf '用法：%s start|stop\n' "$0" >&2; exit 2 ;;
esac
