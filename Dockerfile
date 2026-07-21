# ============================================================
# 应用镜像 — 基于 toolbox-runtime（含 LibreOffice + Chromium + 字体）
# 每次发版只改这一层，秒级构建
# ============================================================
FROM toolbox-runtime:1.1

WORKDIR /app
COPY backend/target/toolbox-1.0.0.jar app.jar

# Playwright driver-bundle（~163MB）不打包进 JAR，而是在镜像中注入
# 日常更新只需传 app.jar（~75MB），driver-bundle 随镜像更新（极少变更）
RUN mkdir -p lib
COPY backend/target/driver-bundle-1.44.0.jar lib/

# 注入 driver-bundle 到 JAR 的 BOOT-INF/lib/
RUN jar xf app.jar BOOT-INF/lib/ && \
    cp lib/driver-bundle-1.44.0.jar BOOT-INF/lib/ && \
    jar uf0 app.jar BOOT-INF/lib/driver-bundle-1.44.0.jar && \
    rm -rf BOOT-INF/ META-INF/ org/ && \
    echo "Injected driver-bundle into JAR"

ENTRYPOINT ["java", "-jar", "app.jar"]
