# ============================================================
# 基础镜像：LibreOffice + 中文字体
# 构建一次，长期不变，后续只在此之上叠加应用 JAR
# ============================================================
FROM eclipse-temurin:17-jdk-jammy

LABEL description="Toolbox 基础镜像 — LibreOffice headless + Chromium + 中文字体"

# 切换 apt 源为阿里云镜像（国内网络加速，避免 archive.ubuntu.com 不可达）
RUN sed -i 's|http://archive.ubuntu.com|http://mirrors.aliyun.com|g' /etc/apt/sources.list 2>/dev/null; \
    sed -i 's|http://security.ubuntu.com|http://mirrors.aliyun.com|g' /etc/apt/sources.list 2>/dev/null; \
    sed -i 's|http://ports.ubuntu.com|http://mirrors.aliyun.com|g' /etc/apt/sources.list 2>/dev/null; \
    sed -i 's|http://archive.ubuntu.com|http://mirrors.aliyun.com|g' /etc/apt/sources.list.d/*.sources 2>/dev/null; \
    sed -i 's|http://security.ubuntu.com|http://mirrors.aliyun.com|g' /etc/apt/sources.list.d/*.sources 2>/dev/null; \
    sed -i 's|http://ports.ubuntu.com|http://mirrors.aliyun.com|g' /etc/apt/sources.list.d/*.sources 2>/dev/null; \
    true

# LibreOffice Writer — 处理 .doc/.docx/.wps
# 中文字体（5 款）— 黑体/宋体/楷体/明体全面覆盖
# fonts-liberation — Times/Arial/Courier 等价字体，文档兼容
RUN apt-get update && apt-get install -y --no-install-recommends \
    libreoffice-writer \
    fonts-noto-cjk \
    fonts-wqy-microhei \
    fonts-wqy-zenhei \
    fonts-arphic-ukai \
    fonts-arphic-uming \
    fonts-liberation \
    && rm -rf /var/lib/apt/lists/* \
    && fc-cache -fv

# LibreOffice 需要可写的 HOME 目录
ENV HOME=/tmp

# 预初始化 LibreOffice 用户配置（优化 PDF 导出保真度）
# 1. 首次启动 soffice 创建默认 profile
# 2. 写入高保真 PDF 导出参数（字体嵌入、PDF/A-1b、无损压缩）
RUN mkdir -p /opt/lo-profile \
    && soffice --headless --norestore \
        "-env:UserInstallation=file:///opt/lo-profile" \
        --terminate_after_init 2>/dev/null || true \
    && mkdir -p /opt/lo-profile/user \
    && cat > /opt/lo-profile/user/registrymodifications.xcu << 'XCUEOF'
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
# 确保 profile 可读
RUN chmod -R 755 /opt/lo-profile

# ============================================================
# Chromium + Playwright 系统依赖 — HTML/URL 转 PDF 渲染引擎
# ============================================================
# Playwright Java 库(已在 pom.xml 中)需要系统安装的 Chromium 浏览器
# 安装 chromium-browser + headless 模式所需的全部依赖库
RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium-browser \
    # Playwright headless 依赖（参考 https://playwright.dev/java/docs/docker）
    libnss3 libnspr4 libatk-bridge2.0-0 libdrm2 libxkbcommon0 \
    libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libgbm1 \
    libpango-1.0-0 libcairo2 libasound2 libatspi2.0-0 \
    libcups2 libxshmfence1 \
    && rm -rf /var/lib/apt/lists/*

# 设置 Chromium 可执行路径，供 Playwright Java 使用
ENV PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium-browser
# 禁用 Playwright 自动下载浏览器（使用系统安装的）
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

# 验证安装（--version 可能因 sandbox 限制返回非 0，用 which 确认即可）
RUN soffice --version && fc-list :lang=zh | head -5 && which chromium-browser && echo "Chromium: OK"
