# ============================================================
# 应用镜像 — 基于 toolbox-base（含 LibreOffice + 字体）
# 每次发版只改这一层，秒级构建
# ============================================================
FROM toolbox-base:1.0

WORKDIR /app
COPY backend/target/toolbox-1.0.0.jar app.jar
EXPOSE 8899

ENTRYPOINT ["java", "-jar", "app.jar"]
