#!/bin/bash
# 启动本机 WebDAV 分享
#
#   用法: ./start.sh [共享目录]
#   不给参数时共享 ~/Downloads
#
# 虚拟环境建在 ~/.file-manager/webdav/.venv (不放进仓库),
# 首次运行会自动创建并安装 wsgidav。

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
STATE_DIR="$HOME/.file-manager/webdav"
VENV="$STATE_DIR/.venv"
PIDFILE="$STATE_DIR/wsgidav.pid"
LOG="$STATE_DIR/server.log"
RUNTIME_CONFIG="$STATE_DIR/wsgidav.yaml"

SHARE="${1:-$HOME/Downloads}"
if [ ! -d "$SHARE" ]; then
  echo "共享目录不存在: $SHARE"
  exit 1
fi
SHARE="$(cd "$SHARE" && pwd)"

# 已运行则直接退出; 显式指定目录时则重启以切换共享目录
if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
  if [ $# -eq 0 ]; then
    echo "已在运行 (pid $(cat "$PIDFILE"))  http://127.0.0.1:8181/"
    exit 0
  fi
  echo "正在切换共享目录, 先停止旧实例..."
  kill "$(cat "$PIDFILE")" 2>/dev/null
  rm -f "$PIDFILE"
  sleep 1
fi

# 首次运行: 建虚拟环境并装依赖
if [ ! -x "$VENV/bin/wsgidav" ]; then
  echo "首次运行, 正在准备环境..."
  mkdir -p "$STATE_DIR"
  if command -v uv >/dev/null 2>&1; then
    uv venv -q "$VENV"
    uv pip install -q --python "$VENV/bin/python" wsgidav cheroot
  else
    python3 -m venv "$VENV"
    "$VENV/bin/pip" install -q --upgrade pip
    "$VENV/bin/pip" install -q wsgidav cheroot
  fi
  echo "环境就绪: $VENV"
fi

# 生成运行配置
mkdir -p "$STATE_DIR"
sed "s|__SHARE_DIR__|$SHARE|" "$SCRIPT_DIR/wsgidav.yaml" > "$RUNTIME_CONFIG"

nohup "$VENV/bin/wsgidav" --config "$RUNTIME_CONFIG" > "$LOG" 2>&1 &
echo $! > "$PIDFILE"
sleep 2

if kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
  echo "已启动 (pid $(cat "$PIDFILE"))  http://127.0.0.1:8181/"
  echo "共享目录: $SHARE"
  echo "默认账号: admin / admin   日志: $LOG"
else
  echo "启动失败, 最后 10 行日志:"
  tail -10 "$LOG"
  rm -f "$PIDFILE"
  exit 1
fi
