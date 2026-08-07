# ============================================================
# 阶段 1：下载 Chromium
# ============================================================
FROM ubuntu:22.04 AS chromium-downloader

RUN apt-get update && apt-get install -y --no-install-recommends wget unzip && \
    rm -rf /var/lib/apt/lists/*

# 下载 Playwright Chromium（跳过证书检查）
# Playwright 1.44.0 需要 chromium-1117
RUN mkdir -p /opt/chromium && \
    cd /tmp && \
    wget --no-check-certificate -q https://playwright.azureedge.net/builds/chromium/1117/chromium-linux.zip -O chromium.zip && \
    unzip -q chromium.zip -d /opt/chromium/ && \
    rm chromium.zip && \
    echo "Chromium downloaded"

# ============================================================
# 阶段 2：应用镜像
# ============================================================
FROM toolbox-runtime:1.1

WORKDIR /app
COPY backend/target/toolbox-1.0.0.jar app.jar

# Tesseract OCR 引擎（含简体中文 + 繁体中文语言包）
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-chi-sim \
    tesseract-ocr-chi-tra \
    && rm -rf /var/lib/apt/lists/*

# Playwright driver-bundle
RUN mkdir -p lib
COPY backend/target/driver-bundle-1.44.0.jar lib/

# 注入 driver-bundle 到 JAR
RUN jar xf app.jar BOOT-INF/lib/ && \
    cp lib/driver-bundle-1.44.0.jar BOOT-INF/lib/ && \
    jar uf0 app.jar BOOT-INF/lib/driver-bundle-1.44.0.jar && \
    rm -rf BOOT-INF/ META-INF/ org/ lib/ && \
    echo "Injected driver-bundle into JAR"

# 从阶段 1 复制 Chromium
ENV PLAYWRIGHT_BROWSERS_PATH=/root/.cache/ms-playwright
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
RUN mkdir -p ${PLAYWRIGHT_BROWSERS_PATH}/chromium-1117
COPY --from=chromium-downloader /opt/chromium/chrome-linux ${PLAYWRIGHT_BROWSERS_PATH}/chromium-1117/chrome-linux

EXPOSE 8899
ENV SERVER_PORT=8899

ENTRYPOINT ["java", "-jar", "app.jar"]
