#!/bin/bash
# ============================================================
# 离线服务器部署脚本（在服务器上执行）
# 前置: toolbox-base-1.0.tar.gz 和 toolbox-lo-1.0.0.tar.gz 已传到 /opt/images/
# ============================================================
set -euo pipefail

echo "===== 1/6 停止旧容器 ====="
if docker ps --format '{{.Names}}' | grep -q '^toolbox$'; then
    echo "停止 toolbox..."
    docker stop toolbox
    echo "删除 toolbox..."
    docker rm toolbox
    echo " ✓ 旧容器已移除"
else
    echo " ⊘ 无运行中的 toolbox 容器"
fi

echo ""
echo "===== 2/6 导入基础镜像（首次需要，后续跳过）====="
BASE_TAR="/opt/images/toolbox-base-1.0.tar.gz"
if [ -f "$BASE_TAR" ]; then
    if ! docker image inspect toolbox-base:1.0 >/dev/null 2>&1; then
        gunzip -k "$BASE_TAR" 2>/dev/null || true
        docker load -i "${BASE_TAR%.gz}"
        echo " ✓ 基础镜像已导入"
    else
        echo " ⊘ 基础镜像已存在，跳过"
    fi
else
    echo " ⊘ 基础镜像文件不存在，跳过（如已导入过则无影响）"
fi

echo ""
echo "===== 3/6 导入应用镜像 ====="
APP_TAR="/opt/images/toolbox-lo-1.0.0.tar.gz"
if [ -f "$APP_TAR" ]; then
    gunzip -k "$APP_TAR" 2>/dev/null || true
    docker load -i "${APP_TAR%.gz}"
    echo " ✓ 应用镜像已导入"
else
    echo "ERROR: 找不到 ${APP_TAR}"
    exit 1
fi

echo ""
echo "===== 4/6 准备 JAR 挂载目录 ====="
JAR_DIR="/opt/toolbox"
JAR_NAME="toolbox-1.0.0.jar"         # 保持 Maven 打包原名
JAR_FILE="${JAR_DIR}/${JAR_NAME}"
mkdir -p "$JAR_DIR"

# 首次部署：从镜像中提取 JAR 到宿主机
if [ ! -f "$JAR_FILE" ]; then
    echo "首次部署，从镜像提取 JAR..."
    docker run --rm --entrypoint cat toolbox-lo:1.0.0 /app/app.jar > "$JAR_FILE"
    echo " ✓ JAR 已提取到 ${JAR_FILE}"
else
    echo " ⊘ JAR 已存在，跳过提取"
fi

echo ""
echo "===== 5/6 初始化 LibreOffice 优化配置 ====="
LO_PROFILE="/opt/lo-profile"
LO_USER="${LO_PROFILE}/user"
mkdir -p "$LO_USER"

# 写入高保真 PDF 导出配置（字体嵌入 + PDF/A-1b + 无损压缩）
cat > "${LO_USER}/registrymodifications.xcu" << 'XCUEOF'
<?xml version="1.0" encoding="UTF-8"?>
<oor:items xmlns:oor="http://openoffice.org/2001/registry" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <item oor:path="/org.openoffice.Office.Common/Filter/PDF/Export">
    <prop oor:name="EmbedFonts" oor:op="fuse"><value>true</value></prop>
    <prop oor:name="EmbedStandardFonts" oor:op="fuse"><value>true</value></prop>
    <prop oor:name="IsSkipEmptyPages" oor:op="fuse"><value>false</value></prop>
    <prop oor:name="UseLosslessCompression" oor:op="fuse"><value>true</value></prop>
    <prop oor:name="ReduceImageResolution" oor:op="fuse"><value>false</value></prop>
    <prop oor:name="SelectPdfVersion" oor:op="fuse"><value>1</value></prop>
    <prop oor:name="ExportBookmarks" oor:op="fuse"><value>true</value></prop>
    <prop oor:name="ExportNotesPages" oor:op="fuse"><value>false</value></prop>
    <prop oor:name="ExportFormFields" oor:op="fuse"><value>true</value></prop>
    <prop oor:name="ExportPlaceholders" oor:op="fuse"><value>false</value></prop>
    <prop oor:name="MaxImageResolution" oor:op="fuse"><value>600</value></prop>
  </item>
</oor:items>
XCUEOF
chmod -R 755 "$LO_PROFILE"
echo " ✓ LibreOffice 优化配置已写入 ${LO_USER}"

echo ""
echo "===== 6/7 验证 ====="
echo "基础镜像:"
docker run --rm toolbox-base:1.0 soffice --version 2>/dev/null || echo "  ⚠ 基础镜像未就绪"
echo "中文字体:"
docker run --rm toolbox-base:1.0 fc-list :lang=zh 2>/dev/null | wc -l | xargs echo "  数量:"

echo ""
echo "===== 7/7 启动服务 ====="
docker run -d \
    --name toolbox \
    -p 8899:8899 \
    -v "${JAR_FILE}:/app/app.jar" \
    -v "${LO_PROFILE}:/opt/lo-profile" \
    --restart unless-stopped \
    toolbox-lo:1.0.0

echo ""
echo "等待服务启动..."
sleep 5
docker logs --tail 20 toolbox

echo ""
echo "============================================"
echo " ✅ 部署完成!"
echo " 访问: http://$(hostname -I 2>/dev/null | awk '{print $1}' || echo '服务器IP'):8899"
echo ""
echo " 后续更新 JAR（无需重新打镜像，Maven 产物直接 scp）:"
echo "   scp backend/target/${JAR_NAME} root@服务器:${JAR_FILE}"
echo "   docker restart toolbox"
echo ""
echo " 常用命令:"
echo "   日志: docker logs -f toolbox"
echo "   验证LO: docker exec toolbox soffice --version"
echo "   验证字体: docker exec toolbox fc-list :lang=zh"
echo "============================================"
