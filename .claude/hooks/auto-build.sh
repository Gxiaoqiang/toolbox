#!/bin/bash
# Auto build hook — 编辑前端或后端文件后自动打包
# 先打前端（输出到 backend/src/main/resources/static/），后打后端 JAR

FILE_PATH="$1"
PROJECT_ROOT="/Users/xiaoqiang/Desktop/GWQ/project/toolbox/toolbox"

# 检查是否是前端或后端源代码文件
case "$FILE_PATH" in
  */frontend/*.vue|*/frontend/*.ts|*/frontend/*.js|*/frontend/*.css)
    echo "[HOOK] Building frontend..."
    cd "$PROJECT_ROOT/frontend" && npm run build 2>&1 | tail -3
    echo "[HOOK] Frontend done"
    echo "[HOOK] Packaging backend JAR..."
    cd "$PROJECT_ROOT/backend" && mvn package -DskipTests -q 2>&1 | tail -5
    echo "[HOOK] Backend JAR done → backend/target/toolbox-1.0.0.jar"
    ;;
  */backend/*.java)
    echo "[HOOK] Building frontend..."
    cd "$PROJECT_ROOT/frontend" && npm run build 2>&1 | tail -3
    echo "[HOOK] Frontend done"
    echo "[HOOK] Packaging backend JAR..."
    cd "$PROJECT_ROOT/backend" && mvn package -DskipTests -q 2>&1 | tail -5
    echo "[HOOK] Backend JAR done → backend/target/toolbox-1.0.0.jar"
    ;;
esac
