#!/bin/bash
# 停止本机 WebDAV 分享
STATE_DIR="$HOME/.file-manager/webdav"
PIDFILE="$STATE_DIR/wsgidav.pid"

if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
  kill "$(cat "$PIDFILE")"
  rm -f "$PIDFILE"
  echo "已停止"
else
  echo "未在运行"
  rm -f "$PIDFILE" 2>/dev/null
fi
