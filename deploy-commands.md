# Docker 部署完整指南

> 日期: 2026-07-22
> 版本: toolbox-lo:1.1.0

---

## 一、本地构建（开发机）

### 1.1 构建运行镜像（一次性，约 15 分钟）

```bash
cd /Users/xiaoqiang/Desktop/GWQ/project/toolbox/toolbox

# 构建运行镜像：JDK + LibreOffice + Chromium + 中文字体
docker build -f runtime.Dockerfile -t toolbox-runtime:1.1 .

# 验证
docker images toolbox-runtime:1.1
```

### 1.2 打包 Docker 模式 JAR

```bash
cd /Users/xiaoqiang/Desktop/GWQ/project/toolbox/toolbox/backend

# -P docker: 排除 driver-bundle（163MB），JAR 约 75MB
mvn clean package -P docker -DskipTests

# 验证 JAR 大小
ls -lh target/toolbox-1.0.0.jar
# 应该约 75MB

# 验证 driver-bundle 已复制到 target
ls -lh target/driver-bundle-1.44.0.jar
# 应该约 163MB
```

### 1.3 构建应用镜像（秒级）

```bash
cd /Users/xiaoqiang/Desktop/GWQ/project/toolbox/toolbox

# 构建应用镜像（注入 driver-bundle 到 JAR）
docker build -t toolbox-lo:1.1.0 .

# 验证
docker images toolbox-lo:1.1.0
```

### 1.4 导出镜像文件

```bash
cd /Users/xiaoqiang/Desktop/GWQ/project/toolbox/toolbox

# 导出运行镜像（首次部署需要）
docker save -o toolbox-runtime-1.1.tar toolbox-runtime:1.1
gzip -f toolbox-runtime-1.1.tar

# 导出应用镜像（每次发版都需要）
docker save -o toolbox-lo-1.1.0.tar toolbox-lo:1.1.0
gzip -f toolbox-lo-1.1.0.tar

# 查看文件大小
ls -lh toolbox-runtime-1.1.tar.gz toolbox-lo-1.1.0.tar.gz
# 预期: runtime ~479MB, app ~934MB
```

### 1.5 清理旧文件（可选）

```bash
rm -f toolbox-base-*.tar.gz toolbox-lo-1.0.*.tar.gz
```

---

## 二、传输到服务器

### 2.1 通过堡垒机上传

上传以下文件到服务器 `/opt/images/` 目录：

| 文件 | 大小 | 说明 |
|------|------|------|
| `toolbox-runtime-1.1.tar.gz` | ~479MB | 运行镜像（首次部署需要） |
| `toolbox-lo-1.1.0.tar.gz` | ~934MB | 应用镜像（每次发版需要） |

---

## 三、服务器部署（首次）

### 3.1 停止旧容器

```bash
docker stop toolbox 2>/dev/null || true
docker rm toolbox 2>/dev/null || true
echo "✓ 旧容器已清理"
```

### 3.2 导入镜像

```bash
cd /opt/images

# 导入运行镜像（首次需要，后续跳过）
gunzip -k toolbox-runtime-1.1.tar.gz 2>/dev/null || true
docker load -i toolbox-runtime-1.1.tar
echo "✓ toolbox-runtime:1.1 已导入"

# 导入应用镜像
gunzip -k toolbox-lo-1.1.0.tar.gz 2>/dev/null || true
docker load -i toolbox-lo-1.1.0.tar
echo "✓ toolbox-lo:1.1.0 已导入"

# 验证镜像
docker images | grep toolbox
```

### 3.3 创建配置文件

```bash
mkdir -p /opt/toolbox

cat > /opt/toolbox/.env << 'EOF'
# ===== LLM 配置 =====
LLM_PROVIDER=deepseek
LLM_MODEL=deepseek-v4-pro
LLM_API_KEY=REDACTED_KEY
LLM_BASE_URL=

# ===== Redis 配置 =====
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=toolbox-redis-2026

# ===== 服务端口 =====
SERVER_PORT=8899
EOF

chmod 600 /opt/toolbox/.env
echo "✓ 配置文件已创建"
```

### 3.4 初始化目录

```bash
# LibreOffice 配置目录
mkdir -p /opt/lo-profile/user

# JAR 存放目录
mkdir -p /opt/toolbox

echo "✓ 目录已创建"
```

### 3.5 提取 JAR

```bash
# 从镜像中提取 JAR（保持原文件名）
docker run --rm --entrypoint cat toolbox-lo:1.1.0 /app/app.jar > /opt/toolbox/toolbox-1.0.0.jar

# 验证
ls -lh /opt/toolbox/toolbox-1.0.0.jar
echo "✓ JAR 已提取"
```

### 3.6 启动容器

```bash
docker run -d \
    --name toolbox \
    -p 8899:8899 \
    -v /opt/toolbox/toolbox-1.0.0.jar:/app/app.jar \
    -v /opt/lo-profile:/opt/lo-profile \
    --env-file /opt/toolbox/.env \
    --add-host=host.docker.internal:host-gateway \
    -e REDIS_HOST=host.docker.internal \
    --restart unless-stopped \
    toolbox-lo:1.1.0
```

**参数说明**：
- `-p 8899:8899` — 端口映射
- `-v .../toolbox-1.0.0.jar:/app/app.jar` — 挂载 JAR（方便后续更新）
- `-v .../lo-profile:/opt/lo-profile` — LibreOffice 配置持久化
- `--env-file` — 加载环境变量配置
- `--add-host=host.docker.internal:host-gateway` — 容器访问宿主机
- `-e REDIS_HOST=host.docker.internal` — Redis 连接宿主机
- `--restart unless-stopped` — 自动重启

### 3.7 验证部署

```bash
# 等待启动
sleep 5

# 查看容器状态
docker ps | grep toolbox

# 查看日志
docker logs --tail 30 toolbox

# 验证服务
curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8899/

# 验证 LibreOffice
docker exec toolbox soffice --version

# 验证 Chromium
docker exec toolbox chromium-browser --version

# 验证中文字体
docker exec toolbox fc-list :lang=zh | head -3
```

---

## 四、后续更新（仅更新 JAR）

### 4.1 本地打包

```bash
cd /Users/xiaoqiang/Desktop/GWQ/project/toolbox/toolbox/backend

# Docker 模式打包
mvn clean package -P docker -DskipTests

# JAR 位置
ls -lh target/toolbox-1.0.0.jar
```

### 4.2 上传到服务器

通过堡垒机上传 `target/toolbox-1.0.0.jar` 到服务器 `/opt/toolbox/toolbox-1.0.0.jar`

### 4.3 重启容器

```bash
# 服务器执行
docker restart toolbox

# 验证
docker logs --tail 10 toolbox
curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8899/
```

---

## 五、常用运维命令

### 5.1 容器管理

```bash
# 查看容器状态
docker ps -a | grep toolbox

# 查看日志（实时）
docker logs -f toolbox

# 查看日志（最近 50 行）
docker logs --tail 50 toolbox

# 进入容器
docker exec -it toolbox bash

# 重启容器
docker restart toolbox

# 停止容器
docker stop toolbox

# 启动容器
docker start toolbox
```

### 5.2 镜像管理

```bash
# 查看镜像
docker images | grep toolbox

# 删除旧镜像（可选）
docker rmi toolbox-base:1.0 2>/dev/null || true
docker rmi toolbox-lo:1.0.0 2>/dev/null || true

# 清理悬空镜像
docker image prune -f
```

### 5.3 验证服务

```bash
# 检查服务健康
curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8899/

# 验证 LibreOffice
docker exec toolbox soffice --version

# 验证 Chromium
docker exec toolbox chromium-browser --version

# 验证中文字体
docker exec toolbox fc-list :lang=zh

# 验证 Redis 连接
docker exec toolbox env | grep REDIS
```

---

## 六、目录结构

### 服务器目录

```
/opt/
├── images/                          # 镜像文件（可清理）
│   ├── toolbox-runtime-1.1.tar.gz   # 运行镜像
│   └── toolbox-lo-1.1.0.tar.gz     # 应用镜像
│
├── toolbox/                         # 应用目录
│   ├── .env                         # 配置文件（权限 600）
│   └── toolbox-1.0.0.jar           # 应用 JAR
│
└── lo-profile/                      # LibreOffice 配置
    └── user/
        └── registrymodifications.xcu
```

### 容器目录

```
/app/
├── app.jar                          # 应用 JAR（挂载自宿主机）
└── lib/
    └── driver-bundle-1.44.0.jar     # Playwright 驱动（镜像内）
```

---

## 七、故障排查

### 7.1 容器无法启动

```bash
# 查看详细日志
docker logs toolbox

# 检查端口占用
netstat -tlnp | grep 8899

# 检查配置文件
cat /opt/toolbox/.env
```

### 7.2 服务无法访问

```bash
# 检查容器是否运行
docker ps | grep toolbox

# 检查端口映射
docker port toolbox

# 检查防火墙
iptables -L -n | grep 8899
```

### 7.3 LibreOffice 问题

```bash
# 验证安装
docker exec toolbox soffice --version

# 检查配置
docker exec toolbox ls -la /opt/lo-profile/user/

# 重新初始化配置
docker exec toolbox soffice --headless --norestore --terminate_after_init
```

### 7.4 Chromium 问题

```bash
# 验证安装
docker exec toolbox chromium-browser --version

# 检查环境变量
docker exec toolbox env | grep PLAYWRIGHT

# 测试渲染
docker exec toolbox chromium-browser --headless --disable-gpu --dump-dom https://example.com
```

---

## 八、版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.1.0 | 2026-07-22 | 新增 Chromium 支持，HTML/URL 转 PDF |
| 1.0.0 | 2026-07-14 | 初始版本，仅 LibreOffice |

---

## 九、注意事项

1. **首次部署**：需要导入 runtime 和 app 两个镜像
2. **后续更新**：只需替换 JAR 文件 + 重启容器
3. **配置文件**：`.env` 权限必须是 600
4. **Redis**：使用 `host.docker.internal` 连接宿主机 Redis
5. **端口**：统一使用 8899
6. **JAR 文件名**：保持 `toolbox-1.0.0.jar` 不变
