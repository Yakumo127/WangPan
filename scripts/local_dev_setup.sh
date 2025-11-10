#!/usr/bin/env bash
set -euo pipefail

# ==============================================
# Enterprise File Manager - Local Dev Setup
# Ubuntu 20.04 fresh machine bootstrap
# Steps:
#   1) Install base environment
#   2) Verify source code structure
#   3) Initialize DB and start backend/frontend
# Each step asks for confirmation.
# Optional: use CN mirrors; if unavailable, use temporary proxy during script only.
# ==============================================

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
# Try to autodetect repository root supporting both layouts:
#  - script at repo_root/scripts/local_dev_setup.sh
#  - script copied to repo_root/local_dev_setup.sh
resolve_repo_root() {
  local cand
  # Prefer the directory where this script resides; if only archives exist there, extract them here.
  if [[ ! -d "$SCRIPT_DIR/backend" && -f "$SCRIPT_DIR/backend.tar" ]]; then
    info "Extracting backend.tar in script directory: $SCRIPT_DIR"
    tar -xf "$SCRIPT_DIR/backend.tar" -C "$SCRIPT_DIR"
  elif [[ ! -d "$SCRIPT_DIR/backend" && -f "$SCRIPT_DIR/backend.tar.gz" ]]; then
    info "Extracting backend.tar.gz in script directory: $SCRIPT_DIR"
    tar -xzf "$SCRIPT_DIR/backend.tar.gz" -C "$SCRIPT_DIR"
  fi
  if [[ ! -d "$SCRIPT_DIR/frontend" && -f "$SCRIPT_DIR/frontend.tar" ]]; then
    info "Extracting frontend.tar in script directory: $SCRIPT_DIR"
    tar -xf "$SCRIPT_DIR/frontend.tar" -C "$SCRIPT_DIR"
  elif [[ ! -d "$SCRIPT_DIR/frontend" && -f "$SCRIPT_DIR/frontend.tar.gz" ]]; then
    info "Extracting frontend.tar.gz in script directory: $SCRIPT_DIR"
    tar -xzf "$SCRIPT_DIR/frontend.tar.gz" -C "$SCRIPT_DIR"
  fi

  for cand in "$SCRIPT_DIR" "$SCRIPT_DIR/.." "$PWD"; do
    if [[ -f "$cand/backend/pom.xml" && -f "$cand/frontend/package.json" ]]; then
      echo "$cand"
      return 0
    fi
  done
  # Not found; ask user
  echo "" 1>&2
  warn "Could not auto-detect repository root."
  read -r -p "Enter repository root path (contains backend/ and frontend/): " cand
  cand=${cand:-$PWD}
  if [[ -f "$cand/backend/pom.xml" && -f "$cand/frontend/package.json" ]]; then
    echo "$cand"
    return 0
  fi
  error "Invalid repository root: $cand"
  return 1
}

REPO_ROOT=$(resolve_repo_root)

HTTP_PROXY_FALLBACK="http://192.168.10.4:10809"
HTTPS_PROXY_FALLBACK="http://192.168.10.4:10809"

USE_CN_MIRRORS=0
USE_TEMP_PROXY=0
PROXY_WAS_SET=0
ENV_OK=0

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

prompt_confirm() {
  local prompt_msg="$1"
  read -r -p "$prompt_msg [Y/n]: " response || true
  case "${response:-Y}" in
    [nN]|[nN][oO]) return 1 ;;
    *) return 0 ;;
  esac
}

enable_temp_proxy() {
  if [[ $PROXY_WAS_SET -eq 0 ]]; then
    export http_proxy="$HTTP_PROXY_FALLBACK"
    export https_proxy="$HTTPS_PROXY_FALLBACK"
    PROXY_WAS_SET=1
    warn "Temporary proxy enabled for this script: $HTTP_PROXY_FALLBACK"
  fi
}

disable_temp_proxy() {
  if [[ $PROXY_WAS_SET -eq 1 ]]; then
    unset http_proxy https_proxy
    PROXY_WAS_SET=0
    info "Temporary proxy disabled."
  fi
}

detect_mirrors_or_proxy() {
  info "Detecting CN mirrors availability..."
  local ok_cnt=0
  if curl -s --connect-timeout 3 https://registry.npmmirror.com >/dev/null; then ok_cnt=$((ok_cnt+1)); fi
  if curl -s --connect-timeout 3 https://maven.aliyun.com/repository/public >/dev/null; then ok_cnt=$((ok_cnt+1)); fi
  if [[ $ok_cnt -ge 1 ]]; then
    USE_CN_MIRRORS=1
    info "CN mirrors reachable; will use npmmirror + Aliyun Maven mirror."
  else
    warn "CN mirrors seem unreachable. You can enable a temporary proxy just for this script."
    if prompt_confirm "Enable temporary proxy $HTTP_PROXY_FALLBACK for this run?"; then
      USE_TEMP_PROXY=1
      enable_temp_proxy
    else
      warn "Proceeding without mirrors or proxy; downloads may be slow."
    fi
  fi
}

apply_maven_mirror_if_needed() {
  if [[ $USE_CN_MIRRORS -eq 1 ]]; then
    local m2_dir="$HOME/.m2"
    mkdir -p "$m2_dir"
    local settings="$m2_dir/settings.xml"
    if [[ ! -f "$settings" ]] || ! grep -q "aliyunmaven" "$settings" 2>/dev/null; then
      info "Configuring Maven Aliyun mirror at $settings"
      cat > "$settings" << 'XML'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyunmaven</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
XML
    else
      info "Maven settings.xml already contains Aliyun mirror; skipping."
    fi
  fi
}

apply_npm_mirror_if_needed() {
  if [[ $USE_CN_MIRRORS -eq 1 ]]; then
    if command -v npm >/dev/null 2>&1; then
      local current
      current=$(npm config get registry || echo "")
      if [[ "$current" != "https://registry.npmmirror.com/" && "$current" != "https://registry.npmmirror.com" ]]; then
        info "Setting npm registry to https://registry.npmmirror.com"
        npm config set registry https://registry.npmmirror.com
      else
        info "npm registry already set to npmmirror."
      fi
    fi
  fi
}

install_base_environment() {
  info "Installing base environment: Java 17, Maven, Node.js 18, MySQL 8, Redis, build tools"

  # Refresh package lists
  sudo apt-get update -y

  # Java 17 JDK
  if ! java -version 2>&1 | grep -q "version \"17"; then
    info "Installing OpenJDK 17..."
    sudo apt-get install -y openjdk-17-jdk || { error "OpenJDK 17 install failed"; return 1; }
  else
    info "Java 17 already present."
  fi

  # Maven
  if ! command -v mvn >/dev/null 2>&1; then
    info "Installing Maven..."
    sudo apt-get install -y maven || { error "Maven install failed"; return 1; }
  else
    info "Maven already present: $(mvn -v | head -n1)"
  fi

  # Node.js 18 via NodeSource
  if ! command -v node >/dev/null 2>&1 || ! node -v | grep -qE '^v18\.'; then
    info "Installing Node.js 18 (NodeSource)..."
    curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
    sudo apt-get install -y nodejs || { error "Node.js install failed"; return 1; }
  else
    info "Node.js already present: $(node -v)"
  fi

  # npm mirror if needed
  apply_npm_mirror_if_needed

  # MySQL server
  if ! dpkg -s mysql-server >/dev/null 2>&1; then
    info "Installing MySQL server..."
    sudo apt-get install -y mysql-server || { error "MySQL install failed"; return 1; }
  fi
  sudo systemctl enable --now mysql || true

  # Redis server
  if ! dpkg -s redis-server >/dev/null 2>&1; then
    info "Installing Redis server..."
    sudo apt-get install -y redis-server || { error "Redis install failed"; return 1; }
  fi
  sudo systemctl enable --now redis-server || true

  # Build essentials
  sudo apt-get install -y build-essential git curl ca-certificates unzip zip

  # Validate tools
  java -version || { error "Java not available"; return 1; }
  mvn -v || { error "Maven not available"; return 1; }
  node -v || { error "Node not available"; return 1; }
  npm -v || { error "npm not available"; return 1; }
  sudo mysql --version || warn "mysql client not in PATH but server should be running"
  redis-cli ping || warn "Redis CLI ping failed"

  info "Base environment installation verified."
}

# ----------- Pre-check environment -----------
precheck_environment() {
  info "Pre-checking base environment..."
  local ok=1

  # Java 17
  if java -version 2>&1 | grep -q 'version "17'; then
    info "Java 17: OK"
  else
    warn "Java 17: MISSING"
    ok=0
  fi

  # Maven
  if command -v mvn >/dev/null 2>&1; then
    info "Maven: $(mvn -v | head -n1)"
  else
    warn "Maven: MISSING"
    ok=0
  fi

  # Node 18
  if command -v node >/dev/null 2>&1 && node -v | grep -qE '^v18\.'; then
    info "Node.js: $(node -v)"
  else
    warn "Node.js 18: MISSING"
    ok=0
  fi

  # MySQL server active
  if sudo mysqladmin ping --silent >/dev/null 2>&1 || systemctl is-active --quiet mysql; then
    info "MySQL: running"
  else
    warn "MySQL: NOT RUNNING/NOT INSTALLED"
    ok=0
  fi

  # Redis server active
  if redis-cli ping >/dev/null 2>&1 || systemctl is-active --quiet redis-server; then
    info "Redis: running"
  else
    warn "Redis: NOT RUNNING/NOT INSTALLED"
    ok=0
  fi

  if [[ $ok -eq 1 ]]; then
    ENV_OK=1
    info "Environment looks OK. You can skip installation to save time."
  else
    ENV_OK=0
    warn "Environment incomplete. Installation is recommended."
  fi
}

maybe_extract_archives() {
  # Always operate relative to the script directory as requested.
  # If backend/frontend directories are missing but archives exist alongside the script, extract them here.
  if [[ ! -d "$SCRIPT_DIR/backend" ]]; then
    if [[ -f "$SCRIPT_DIR/backend.tar" ]]; then
      info "Extracting backend.tar in $SCRIPT_DIR ..."
      tar -xf "$SCRIPT_DIR/backend.tar" -C "$SCRIPT_DIR"
    elif [[ -f "$SCRIPT_DIR/backend.tar.gz" ]]; then
      info "Extracting backend.tar.gz in $SCRIPT_DIR ..."
      tar -xzf "$SCRIPT_DIR/backend.tar.gz" -C "$SCRIPT_DIR"
    fi
  fi
  if [[ ! -d "$SCRIPT_DIR/frontend" ]]; then
    if [[ -f "$SCRIPT_DIR/frontend.tar" ]]; then
      info "Extracting frontend.tar in $SCRIPT_DIR ..."
      tar -xf "$SCRIPT_DIR/frontend.tar" -C "$SCRIPT_DIR"
    elif [[ -f "$SCRIPT_DIR/frontend.tar.gz" ]]; then
      info "Extracting frontend.tar.gz in $SCRIPT_DIR ..."
      tar -xzf "$SCRIPT_DIR/frontend.tar.gz" -C "$SCRIPT_DIR"
    fi
  fi
}

verify_repo_structure() {
  info "Verifying repository structure under $REPO_ROOT"
  maybe_extract_archives
  local ok=1
  [[ -f "$REPO_ROOT/backend/pom.xml" ]] || { error "Missing backend/pom.xml"; ok=0; }
  [[ -d "$REPO_ROOT/backend/src/main/java" ]] || { error "Missing backend/src/main/java"; ok=0; }
  [[ -f "$REPO_ROOT/backend/src/main/resources/application.yml" ]] || { error "Missing backend/src/main/resources/application.yml"; ok=0; }

  [[ -f "$REPO_ROOT/frontend/package.json" ]] || { error "Missing frontend/package.json"; ok=0; }
  [[ -d "$REPO_ROOT/frontend/src" ]] || { error "Missing frontend/src"; ok=0; }

  if [[ $ok -eq 0 ]]; then
    error "Repository structure invalid; aborting."
    return 1
  fi
  info "Repository structure OK."
}

wait_for_mysql() {
  info "Waiting for MySQL to be ready..."
  for i in {1..30}; do
    if sudo mysqladmin ping --silent >/dev/null 2>&1; then
      info "MySQL is ready."
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_for_http() {
  local url="$1"; local name="$2"; local tries=${3:-60}
  info "Waiting for $name at $url ..."
  for i in $(seq 1 "$tries"); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      info "$name is up."
      return 0
    fi
    sleep 1
  done
  return 1
}

# ----------- Port pre-check and interactive close -----------
print_port_occupants() {
  local port="$1"
  local pids; pids=$(find_pids_by_port "$port")
  if [[ -z "$pids" ]]; then
    info "Port $port is free."
    return 0
  fi
  warn "Port $port in use by:"
  local pid
  for pid in $pids; do
    local comm cmd
    comm=$(ps -o comm= -p "$pid" 2>/dev/null || echo "?")
    cmd=$(ps -o args= -p "$pid" 2>/dev/null || echo "?")
    echo "  PID=$pid  COMM=$comm  CMD=$cmd"
  done
}

precheck_ports_and_prompt() {
  info "Checking if ports 3000/8080/8888 are occupied..."
  local ports=(3000 8080 8888)
  local names=(frontend backend health)
  local i
  for i in 0 1 2; do
    local port="${ports[$i]}"; local name="${names[$i]}"
    local pids; pids=$(find_pids_by_port "$port")
    if [[ -n "$pids" ]]; then
      print_port_occupants "$port"
      if prompt_confirm "Close processes on port $port ($name) before starting?"; then
        local pid
        for pid in $pids; do
          kill_pid_gracefully "$pid" "${name}-port-${port}"
        done
        # Double-check
        free_port "$port" "$name"
      else
        warn "Keep port $port occupied; startup may fail if the service needs it."
      fi
    else
      info "Port $port is free."
    fi
  done
}

init_db_and_start_services() {
  # Ensure services are running
  sudo systemctl enable --now mysql || true
  sudo systemctl enable --now redis-server || true

  wait_for_mysql || { error "MySQL did not become ready"; return 1; }

  info "Creating database and user (idempotent)..."
  sudo mysql -e "CREATE DATABASE IF NOT EXISTS enterprise_file_manager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  sudo mysql -e "CREATE USER IF NOT EXISTS 'filemanager'@'localhost' IDENTIFIED BY 'filemanager_password';"
  sudo mysql -e "GRANT ALL PRIVILEGES ON enterprise_file_manager.* TO 'filemanager'@'localhost'; FLUSH PRIVILEGES;"

  info "Verifying Redis..."
  redis-cli ping || warn "Redis ping failed (continuing)"

  info "Preparing local storage directories..."
  sudo mkdir -p /tmp/cunchu /tmp/temp
  sudo chmod 0777 -R /tmp/cunchu /tmp/temp || true

  info "Building backend dependencies (first run may take time)..."
  (cd "$REPO_ROOT/backend" && mvn -q -DskipTests package) || { error "Backend build failed"; return 1; }

  info "Starting backend (Spring Boot)..."
  (cd "$REPO_ROOT/backend" && nohup mvn spring-boot:run > "$REPO_ROOT/backend.log" 2>&1 & echo $! > /tmp/efm_backend.pid)

  wait_for_http "http://localhost:8080/actuator/health" "backend" 90 || { error "Backend did not become healthy"; return 1; }

  info "Installing frontend dependencies..."
  (cd "$REPO_ROOT/frontend" && npm install) || { error "Frontend npm install failed"; return 1; }

  info "Starting frontend dev server (port 3000)..."
  # Use node to invoke vue-cli-service to avoid exec permission issues on noexec mounts
  (cd "$REPO_ROOT/frontend" && \
    nohup bash -lc 'if [ -f node_modules/@vue/cli-service/bin/vue-cli-service.js ]; then \
        exec node node_modules/@vue/cli-service/bin/vue-cli-service.js serve --host 0.0.0.0 --port 3000; \
      else \
        exec npx @vue/cli-service serve --host 0.0.0.0 --port 3000; \
      fi' > "$REPO_ROOT/frontend.log" 2>&1 & echo $! > /tmp/efm_frontend.pid)

  wait_for_http "http://localhost:3000" "frontend" 60 || { warn "Frontend dev server not responding on port 3000 yet"; }

  info "All services started."
  echo
  echo "Endpoints:"
  echo "  Frontend:   http://localhost:3000"
  echo "  Backend:    http://localhost:8080"
  echo "  Health:     http://localhost:8080/actuator/health"
  echo "Default Accounts:"
  echo "  Admin: admin / admin123"
}

# ----------- Stop services & free ports -----------
get_pid_from_file() {
  local file="$1"
  [[ -f "$file" ]] || return 1
  local pid
  pid=$(cat "$file" 2>/dev/null || true)
  [[ -n "${pid:-}" ]] || return 1
  if kill -0 "$pid" 2>/dev/null; then
    echo "$pid"
    return 0
  fi
  return 1
}

kill_pid_gracefully() {
  local pid="$1"; local name="$2"
  if ! kill -0 "$pid" 2>/dev/null; then return 0; fi
  warn "Stopping $name (pid=$pid) ..."
  kill "$pid" 2>/dev/null || true
  for i in {1..10}; do
    if ! kill -0 "$pid" 2>/dev/null; then
      info "$name stopped."
      return 0
    fi
    sleep 1
  done
  warn "$name did not exit gracefully, killing..."
  kill -9 "$pid" 2>/dev/null || true
}

find_pids_by_port() {
  local port="$1"
  # Try lsof
  if command -v lsof >/dev/null 2>&1; then
    lsof -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true
    return 0
  fi
  # Try fuser
  if command -v fuser >/dev/null 2>&1; then
    fuser -n tcp "$port" 2>/dev/null | tr ' ' '\n' || true
    return 0
  fi
  # Fallback: parse ss output
  if command -v ss >/dev/null 2>&1; then
    ss -ltnp 2>/dev/null \
      | awk -v p=":$port" '$4 ~ p {print $NF}' \
      | sed -nE 's/.*pid=([0-9]+).*/\1/p' || true
    return 0
  fi
  # Fallback: netstat
  if command -v netstat >/dev/null 2>&1; then
    netstat -ltnp 2>/dev/null \
      | awk -v p=":$port" '$4 ~ p {print $7}' \
      | sed -nE 's#^([0-9]+)/.*$#\1#p' || true
    return 0
  fi
}

free_port() {
  local port="$1"; local name="$2"
  local pids
  pids=$(find_pids_by_port "$port")
  if [[ -z "$pids" ]]; then
    info "Port $port is free."
    return 0
  fi
  warn "Port $port in use by PIDs: $pids"
  for pid in $pids; do
    kill_pid_gracefully "$pid" "${name}-port-${port}"
  done
}

stop_services_and_free_ports() {
  info "Stopping frontend/backend if running and freeing ports 3000/8080"
  local fe_pid be_pid
  fe_pid=$(get_pid_from_file /tmp/efm_frontend.pid || true)
  be_pid=$(get_pid_from_file /tmp/efm_backend.pid || true)

  if [[ -n "${fe_pid:-}" ]]; then
    kill_pid_gracefully "$fe_pid" "frontend"
    rm -f /tmp/efm_frontend.pid
  fi
  if [[ -n "${be_pid:-}" ]]; then
    kill_pid_gracefully "$be_pid" "backend"
    rm -f /tmp/efm_backend.pid
  fi

  # Also free ports in case PIDs not tracked
  free_port 3000 "frontend"
  free_port 8080 "backend"

  info "Ports freed (3000/8080)."
}

stop_frontend_only() {
  info "Stopping frontend and freeing port 3000"
  local fe_pid
  fe_pid=$(get_pid_from_file /tmp/efm_frontend.pid || true)
  if [[ -n "${fe_pid:-}" ]]; then
    kill_pid_gracefully "$fe_pid" "frontend"
    rm -f /tmp/efm_frontend.pid
  fi
  free_port 3000 "frontend"
}

stop_backend_only() {
  info "Stopping backend and freeing port 8080"
  local be_pid
  be_pid=$(get_pid_from_file /tmp/efm_backend.pid || true)
  if [[ -n "${be_pid:-}" ]]; then
    kill_pid_gracefully "$be_pid" "backend"
    rm -f /tmp/efm_backend.pid
  fi
  free_port 8080 "backend"
}

stop_health_server() {
  # Allow user to specify a health server port, default 8888
  local port
  read -r -p "Enter health server port to stop [default 8888]: " port || true
  port=${port:-8888}
  info "Freeing health server port $port"
  free_port "$port" "health"
}

main() {
  info "Enterprise File Manager - Local Dev Setup"
  precheck_environment
  if [[ $ENV_OK -eq 1 ]]; then
    if prompt_confirm "Base env OK. Skip installation step?"; then
      warn "Skipping mirrors/proxy detection and installation."
    else
      detect_mirrors_or_proxy
      apply_maven_mirror_if_needed
      if prompt_confirm "Step 1/3: Install base environment?"; then
        install_base_environment || { disable_temp_proxy; exit 1; }
      else
        warn "Skipped base environment installation."
      fi
    fi
  else
    detect_mirrors_or_proxy
    apply_maven_mirror_if_needed
    if prompt_confirm "Step 1/3: Install base environment?"; then
      install_base_environment || { disable_temp_proxy; exit 1; }
    else
      warn "Skipped base environment installation (not recommended)."
    fi
  fi

  if prompt_confirm "Step 2/3: Verify repository structure?"; then
    verify_repo_structure || { disable_temp_proxy; exit 1; }
  else
    warn "Skipped repository verification."
  fi

  # Always pre-check ports before starting services
  precheck_ports_and_prompt

  if prompt_confirm "Step 3/3: Initialize DB and start backend/frontend?"; then
    init_db_and_start_services || { disable_temp_proxy; exit 1; }
  else
    warn "Skipped initialization and startup."
  fi

  # Optional: stop services and/or free ports
  echo
  if prompt_confirm "Extra: Stop services and free ports?"; then
    echo "Select an option:"
    echo "  1) Stop ALL (frontend+backend+health-port)"
    echo "  2) Stop FRONTEND only (port 3000)"
    echo "  3) Stop BACKEND only (port 8080)"
    echo "  4) Stop HEALTH server (custom port, default 8888)"
    read -r -p "Enter choice [1-4]: " choice || true
    case "${choice:-1}" in
      1)
        stop_services_and_free_ports || true
        stop_health_server || true
        ;;
      2)
        stop_frontend_only || true
        ;;
      3)
        stop_backend_only || true
        ;;
      4)
        stop_health_server || true
        ;;
      *)
        warn "Unknown choice; skipping stop."
        ;;
    esac
  else
    warn "Skipped stopping services."
  fi

  disable_temp_proxy
  info "Done."
}

main "$@"
