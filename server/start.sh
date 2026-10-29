#!/usr/bin/env bash
# ============================================================================
#  shine星心 外贸独立站 · 一键部署启动脚本（Linux / macOS）
#  ---------------------------------------------------------------------------
#  用法：
#     ./start.sh                 # 自动准备环境 → 构建 → 启动
#     ./start.sh --skip-build    # 已有 jar 时跳过构建，直接启动
#     ./start.sh --port=9090     # 指定端口启动
#     ./start.sh --prod          # 以 prod 配置启动（需先在 env 或 application-prod 配置数据库）
#     ./start.sh --stop          # 停止正在运行的实例
#     ./start.sh --help          # 查看帮助
#
#  设计原则：
#    · 全部依赖（JDK / Maven）只安装到本项目 .tools/ 目录，不污染系统环境
#    · 数据、上传文件全部落在项目目录（runtime/、uploads/），卸载只需删除目录
#    · 断网时若本地已有依赖，仍可正常启动
# ============================================================================
set -euo pipefail

# ---------- 基础变量 ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

TOOLS_DIR="$SCRIPT_DIR/.tools"
# 仓库按 server/ + android/ 分类后，JDK/Maven 缓存可能仍留在仓库根的 .tools
if [ ! -d "$TOOLS_DIR" ] && [ -d "$SCRIPT_DIR/../.tools" ]; then
  TOOLS_DIR="$(cd "$SCRIPT_DIR/../.tools" && pwd)"
fi
JDK_DIR="$TOOLS_DIR/jdk17"
MVN_DIR="$TOOLS_DIR/maven"
RUNTIME_DIR="$SCRIPT_DIR/runtime"
UPLOAD_DIR="$SCRIPT_DIR/uploads"
LOG_DIR="$RUNTIME_DIR/logs"
PID_FILE="$RUNTIME_DIR/app.pid"
JAR_PATH="$SCRIPT_DIR/target/shine-xumis.jar"

JDK_MAJOR_MIN=17
MAVEN_VERSION="3.9.9"
_PORT=""
_PROFILE="dev"
_SKIP_BUILD=0
_DO_STOP=0

# ---------- 参数解析 ----------
for arg in "$@"; do
  case "$arg" in
    --skip-build) _SKIP_BUILD=1 ;;
    --prod)       _PROFILE="prod" ;;
    --h2)         _PROFILE="h2" ;;       # 应急：无 MySQL 时用内置文件库预览
    --stop)       _DO_STOP=1 ;;
    --port=*)     _PORT="${arg#*=}" ;;
    -h|--help)    sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "未知参数: $arg （使用 --help 查看用法）"; exit 1 ;;
  esac
done

# ---------- 彩色输出 ----------
if [ -t 1 ]; then
  C_RESET="\033[0m"; C_INFO="\033[36m"; C_OK="\033[32m"; C_WARN="\033[33m"; C_ERR="\033[31m"
else
  C_RESET=""; C_INFO=""; C_OK=""; C_WARN=""; C_ERR=""
fi
info() { printf "${C_INFO}[信息]${C_RESET} %s\n" "$1"; }
ok()   { printf "${C_OK}[完成]${C_RESET} %s\n" "$1"; }
warn() { printf "${C_WARN}[提示]${C_RESET} %s\n" "$1"; }
err()  { printf "${C_ERR}[错误]${C_RESET} %s\n" "$1" >&2; }

# ---------- 步骤 0：停止实例 ----------
if [ "$_DO_STOP" -eq 1 ]; then
  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    kill "$(cat "$PID_FILE")" && rm -f "$PID_FILE"
    ok "已停止站点进程。"
  else
    warn "未发现运行中的实例（PID 文件不存在或进程已退出）。"
  fi
  exit 0
fi

# ---------- 步骤 1：创建目录 ----------
mkdir -p "$TOOLS_DIR" "$RUNTIME_DIR" "$UPLOAD_DIR" "$LOG_DIR" "$SCRIPT_DIR/data"
info "工作目录：$SCRIPT_DIR"

# ---------- 步骤 2：检测 / 安装 JDK 17 ----------
# 在本地 .tools/jdk17 中查找 java（兼容 Linux 的 bin/java 与 macOS 的 */Contents/Home/bin/java）
find_local_java() {
  [ -d "$JDK_DIR" ] || return 0
  find "$JDK_DIR" -type f -path "*/bin/java" -perm -u+x 2>/dev/null | head -n 1
}

detect_java() {
  local candidate="$1" bin
  [ -n "$candidate" ] || return 1
  if [ -f "$candidate" ] && [ -x "$candidate" ]; then bin="$candidate"
  elif [ -x "$candidate/bin/java" ]; then bin="$candidate/bin/java"
  elif [ -x "$candidate/bin/javac" ]; then bin="$candidate/bin/java"
  else command -v "$candidate" >/dev/null 2>&1 && bin="$candidate" || return 1
  fi
  local ver; ver="$("$bin" -version 2>&1 | head -n 1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  [ -n "$ver" ] && [ "$ver" -ge "$JDK_MAJOR_MIN" ] && { JAVA_BIN="$bin"; JAVA_VER="$ver"; return 0; }
  return 1
}

# 带重试与完整性校验的下载（防止网络/代理截断导致解压不完整）
download_with_retry() {
  local url="$1" out="$2" tries=4 i
  for i in $(seq 1 "$tries"); do
    curl -fsSL --max-time 900 -o "$out" "$url" || { sleep 3; continue; }
    if tar -tzf "$out" >/dev/null 2>&1; then return 0; fi
    warn "下载文件不完整（可能被截断），重试 $i/$tries ..."
    sleep 3
  done
  return 1
}

info "步骤 1/4：检查 Java 运行环境（需要 JDK ${JDK_MAJOR_MIN}+）"
JAVA_BIN=""; JAVA_VER=""
if detect_java "${JAVA_HOME:-}"; then
  ok "使用 JAVA_HOME 的 Java $JAVA_VER"
elif detect_java "$(command -v java || true)"; then
  ok "使用系统 Java $JAVA_VER"
else
  LOCAL_JAVA="$(find_local_java)"
  if [ -n "$LOCAL_JAVA" ] && detect_java "$LOCAL_JAVA"; then
    ok "使用本地自带 Java $JAVA_VER（$LOCAL_JAVA）"
  fi
fi

if [ -z "$JAVA_BIN" ]; then
  warn "未找到 JDK ${JDK_MAJOR_MIN}+，尝试自动下载到 .tools/jdk17（仅本项目内，不影响系统）..."
  OS_NAME="$(uname -s)"; ARCH_NAME="$(uname -m)"
  case "$OS_NAME" in Darwin) ADOPT_OS="mac";; *) ADOPT_OS="linux";; esac
  case "$ARCH_NAME" in arm64|aarch64) ADOPT_ARCH="aarch64";; x86_64|amd64) ADOPT_ARCH="x64";; *) ADOPT_ARCH="x64";; esac
  ADOPT_URL="https://api.adoptium.net/v3/binary/latest/17/ga/${ADOPT_OS}/${ADOPT_ARCH}/jdk/hotspot/normal/eclipse"
  if download_with_retry "$ADOPT_URL" "$TOOLS_DIR/jdk17.tar.gz"; then
    rm -rf "$JDK_DIR"; mkdir -p "$JDK_DIR"
    tar -xzf "$TOOLS_DIR/jdk17.tar.gz" -C "$JDK_DIR" --strip-components=1
    rm -f "$TOOLS_DIR/jdk17.tar.gz"
    ok "JDK 17 已解压到 .tools/jdk17"
    LOCAL_JAVA="$(find_local_java)"
    if [ -z "$LOCAL_JAVA" ] || ! detect_java "$LOCAL_JAVA"; then
      err "解压后的 JDK 不可用，请手动安装 JDK 17 后重试。"; exit 1
    fi
  else
    err "自动下载 JDK 失败。请手动安装 JDK 17+ 并设置 JAVA_HOME，或联网后重试。"
    err "下载地址：https://adoptium.net/temurin/releases/?version=17"
    exit 1
  fi
fi
export JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
export PATH="$JAVA_HOME/bin:$PATH"
ok "Java：$JAVA_BIN（版本 $JAVA_VER）"

# ---------- 步骤 3：检测 / 安装 Maven ----------
info "步骤 2/4：检查 Maven（需要 3.6+）"
MVN_BIN=""
if command -v mvn >/dev/null 2>&1; then
  MVN_BIN="$(command -v mvn)"; ok "使用系统 Maven：$MVN_BIN"
elif [ -x "$MVN_DIR/bin/mvn" ]; then
  MVN_BIN="$MVN_DIR/bin/mvn"; ok "使用本地自带 Maven：$MVN_BIN"
fi

if [ -z "$MVN_BIN" ]; then
  warn "未找到 Maven，尝试自动下载到 .tools/maven（仅本项目内）..."
  MVN_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  MVN_URL_FALLBACK="https://repo1.maven.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  if download_with_retry "$MVN_URL" "$TOOLS_DIR/maven.tar.gz" \
     || download_with_retry "$MVN_URL_FALLBACK" "$TOOLS_DIR/maven.tar.gz"; then
    rm -rf "$MVN_DIR"; mkdir -p "$MVN_DIR"
    tar -xzf "$TOOLS_DIR/maven.tar.gz" -C "$MVN_DIR" --strip-components=1
    rm -f "$TOOLS_DIR/maven.tar.gz"
    chmod +x "$MVN_DIR/bin/mvn"
    MVN_BIN="$MVN_DIR/bin/mvn"
    ok "Maven $MAVEN_VERSION 已解压到 .tools/maven"
  else
    err "自动下载 Maven 失败。请手动安装 Maven 3.6+ 后重试，或联网后重试。"
    exit 1
  fi
fi

# ---------- 步骤 4：构建 ----------
if [ "$_SKIP_BUILD" -eq 1 ] && [ -f "$JAR_PATH" ]; then
  info "步骤 3/4：跳过构建（--skip-build），使用已有 $JAR_PATH"
else
  info "步骤 3/4：编译打包（首次会下载依赖，约 1-3 分钟）..."
  "$MVN_BIN" -B -q clean package -DskipTests
  ok "构建完成：$JAR_PATH"
fi

if [ ! -f "$JAR_PATH" ]; then
  err "未找到产物 $JAR_PATH，构建可能失败，请查看上方日志。"
  exit 1
fi

# ---------- 步骤 4.5：检查 MySQL 是否可用 ----------
# 站点默认使用 MySQL（唯一数据源）。这里只做提示，不阻断启动。
DB_HOST="${DB_HOST:-127.0.0.1}"; DB_PORT_ENV="${DB_PORT:-3306}"
DB_NAME_ENV="${DB_NAME:-shine}"
info "步骤 3.5/4：检查 MySQL（${DB_HOST}:${DB_PORT_ENV}，库 ${DB_NAME_ENV}）"
_mysql_ok=0
if command -v nc >/dev/null 2>&1; then
  nc -z -G 2 "$DB_HOST" "$DB_PORT_ENV" >/dev/null 2>&1 && _mysql_ok=1
else
  (echo > "/dev/tcp/${DB_HOST}/${DB_PORT_ENV}") >/dev/null 2>&1 && _mysql_ok=1
fi
if [ "$_mysql_ok" -eq 1 ]; then
  ok "MySQL 可连接（库不存在时会用 createDatabaseIfNotExist 自动创建）"
else
  warn "连不上 MySQL（${DB_HOST}:${DB_PORT_ENV}），启动后可能报错。"
  warn "  · macOS 启动：sudo /usr/local/mysql/support-files/mysql.server start"
  warn "  · 或用 brew：brew services start mysql"
  warn "  · 指定账号：DB_HOST=... DB_PORT=3306 DB_NAME=shine DB_USER=root DB_PASSWORD=xxx ./start.sh"
  warn "  · 临时用内置文件库预览：./start.sh --h2"
fi

# ---------- 步骤 5：启动 ----------
if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  warn "检测到已在运行的实例（PID $(cat "$PID_FILE")），先停止..."
  kill "$(cat "$PID_FILE")" 2>/dev/null || true
  sleep 2
fi

info "步骤 4/4：启动站点（profile=$_PROFILE）..."
PORT_ARG=""
[ -n "$_PORT" ] && PORT_ARG="--server.port=${_PORT}"

nohup "$JAVA_HOME/bin/java" \
  -Dfile.encoding=UTF-8 \
  -jar "$JAR_PATH" \
  --spring.profiles.active="$_PROFILE" \
  $PORT_ARG \
  > "$LOG_DIR/app.log" 2>&1 &
echo $! > "$PID_FILE"

# 等待端口就绪
PORT_NO="${_PORT:-8080}"
printf "%s" "等待服务启动"
for i in $(seq 1 60); do
  if curl -fsS --noproxy '*' "http://127.0.0.1:${PORT_NO}/" >/dev/null 2>&1; then echo; ok "服务已就绪"; break; fi
  printf "."; sleep 2
  [ "$i" -eq 60 ] && { echo; err "启动超时，请查看日志：$LOG_DIR/app.log"; exit 1; }
done

echo ""
echo "============================================================"
ok " shine星心 独立站已启动"
echo "   前台首页   http://localhost:${PORT_NO}/"
echo "   后台管理   http://localhost:${PORT_NO}/admin?key=admin"
echo "   日志       $LOG_DIR/app.log"
echo "   停止服务   ./start.sh --stop"
echo "============================================================"
