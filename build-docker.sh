#!/bin/bash
# ============================================================
# toolbox Docker 构建脚本
# 用法:
#   bash build-docker.sh base     — 构建运行镜像（LibreOffice+字体，一次性）
#   bash build-docker.sh app      — 构建应用镜像（秒级）
#   bash build-docker.sh all      — 两者都构建
# ============================================================
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
RUNTIME_IMAGE="toolbox-runtime"
BASE_TAG="1.1"
APP_IMAGE="toolbox-lo"
APP_TAG="1.1.0"

usage() {
  echo "用法: bash build-docker.sh [base|app|all]"
  echo "  base — 构建运行镜像 (LibreOffice+字体)，构建一次即可"
  echo "  app  — 构建应用镜像，仅 COPY JAR，秒级完成"
  echo "  all  — 两者都构建并导出 tar.gz"
  exit 1
}

MODE="${1:-all}"
case "$MODE" in
  base|app|all) ;;
  *) usage ;;
esac

# ---- 构建前端 + JAR（app 或 all 模式需要） ----
if [[ "$MODE" == "app" || "$MODE" == "all" ]]; then
  if [ ! -f "${PROJECT_ROOT}/backend/src/main/resources/static/index.html" ]; then
    echo "[pre] 构建前端..."
    cd "${PROJECT_ROOT}/frontend"
    npm run build
  fi
  echo "[pre] 构建后端 JAR..."
  cd "${PROJECT_ROOT}/backend"
  mvn clean package -P docker -DskipTests -q
  echo "  ✓ JAR: $(ls -lh target/toolbox-1.0.0.jar | awk '{print $5}')"
fi

# ---- 运行镜像 ----
if [[ "$MODE" == "base" || "$MODE" == "all" ]]; then
  echo ""
  echo "===== 构建运行镜像 (LibreOffice + 字体) ====="
  cd "${PROJECT_ROOT}"
  docker build --progress=plain -f runtime.Dockerfile -t ${RUNTIME_IMAGE}:${BASE_TAG} .
  echo "  ✓ 运行镜像: ${RUNTIME_IMAGE}:${BASE_TAG}"
  docker images ${RUNTIME_IMAGE}:${BASE_TAG}

  # 导出运行镜像
  RUNTIME_TAR="${RUNTIME_IMAGE}-${BASE_TAG}.tar"
  echo ""
  echo "导出运行镜像..."
  docker save -o "${RUNTIME_TAR}" ${RUNTIME_IMAGE}:${BASE_TAG}
  gzip -f "${RUNTIME_TAR}"
  echo "  ✓ ${RUNTIME_TAR}.gz ($(ls -lh ${RUNTIME_TAR}.gz | awk '{print $5}'))"
fi

# ---- 应用镜像 ----
if [[ "$MODE" == "app" || "$MODE" == "all" ]]; then
  # 检查运行镜像是否存在
  if ! docker image inspect ${RUNTIME_IMAGE}:${BASE_TAG} >/dev/null 2>&1; then
    echo "ERROR: 运行镜像 ${RUNTIME_IMAGE}:${BASE_TAG} 不存在，请先运行: bash build-docker.sh base"
    exit 1
  fi

  echo ""
  echo "===== 构建应用镜像 (仅 JAR 层) ====="
  cd "${PROJECT_ROOT}"
  docker build --progress=plain -t ${APP_IMAGE}:${APP_TAG} .
  echo "  ✓ 应用镜像: ${APP_IMAGE}:${APP_TAG}"
  docker images ${APP_IMAGE}:${APP_TAG}

  # 导出应用镜像
  APP_TAR="${APP_IMAGE}-${APP_TAG}.tar"
  echo ""
  echo "导出应用镜像..."
  docker save -o "${APP_TAR}" ${APP_IMAGE}:${APP_TAG}
  gzip -f "${APP_TAR}"
  echo "  ✓ ${APP_TAR}.gz ($(ls -lh ${APP_TAR}.gz | awk '{print $5}'))"
fi

echo ""
echo "============================================"
echo " ✅ 完成!"
echo ""
if [[ "$MODE" == "base" || "$MODE" == "all" ]]; then
  echo " 运行镜像: ${RUNTIME_IMAGE}-${BASE_TAG}.tar.gz"
  echo "   → 传输到服务器后: docker load -i ${RUNTIME_IMAGE}-${BASE_TAG}.tar.gz"
  echo "   → 此镜像长期不变，后续只需传输应用镜像"
fi
if [[ "$MODE" == "app" || "$MODE" == "all" ]]; then
  echo " 应用镜像: ${APP_IMAGE}-${APP_TAG}.tar.gz"
fi
echo ""
echo " 服务器运行:"
echo "   docker run -d --name toolbox -p 8899:8899 --restart unless-stopped ${APP_IMAGE}:${APP_TAG}"
echo "============================================"
