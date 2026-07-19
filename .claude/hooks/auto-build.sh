#!/bin/bash
# Auto build hook — 编辑前端或后端文件后自动打包
# 先打前端，后打后端

FILE_PATH="$1"
PROJECT_ROOT="/Users/xiaoqiang/Desktop/GWQ/project/toolbox/toolbox"

# 检查是否是前端或后端源代码文件
case "$FILE_PATH" in
  */frontend/*.vue|*/frontend/*.ts|*/frontend/*.js|*/frontend/*.css)
    echo "[HOOK] Building frontend..."
    cd "$PROJECT_ROOT/frontend" && npm run build 2>&1 | tail -3
    echo "[HOOK] Frontend done"
    echo "[HOOK] Building backend..."
    cd "$PROJECT_ROOT/backend" && mvn compile -q 2>&1 | tail -5
    echo "[HOOK] Backend done"
    ;;
  */backend/*.java)
    echo "[HOOK] Building frontend..."
    cd "$PROJECT_ROOT/frontend" && npm run build 2>&1 | tail -3
    echo "[HOOK] Frontend done"
    echo "[HOOK] Building backend..."
    cd "$PROJECT_ROOT/backend" && mvn compile -q 2>&1 | tail -5
    echo "[HOOK] Backend done"
    ;;
esac
