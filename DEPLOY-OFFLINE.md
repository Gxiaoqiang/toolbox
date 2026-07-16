# 离线 Linux 服务器 — toolbox Docker 部署指南

> 镜像分两层：**基础镜像**（LibreOffice+字体，一次性部署）+ **应用镜像**（仅 JAR，秒级构建）

---

## 一、首次部署 — 本地构建两个镜像

### 前提
- Docker 已启动，网络正常
- JDK 17 + Maven + Node.js 已安装

```bash
cd /path/to/toolbox
chmod +x build-docker.sh

# 1. 构建基础镜像 + 导出（只需一次，~5-10 分钟）
bash build-docker.sh base

# 2. 构建应用镜像 + 导出（秒级）
bash build-docker.sh app
```

产物:
- `toolbox-base-1.0.tar.gz`（~500MB，LibreOffice + 字体）
- `toolbox-lo-1.0.0.tar.gz`（~60MB，仅应用 JAR）

### 传输到离线服务器

```bash
scp toolbox-base-1.0.tar.gz toolbox-lo-1.0.0.tar.gz root@192.168.1.100:/opt/images/
```

---

## 二、离线服务器 — 首次部署

```bash
cd /opt/images/

# 1. 导入基础镜像（只需一次）
gunzip toolbox-base-1.0.tar.gz
docker load -i toolbox-base-1.0.tar

# 2. 导入应用镜像
gunzip toolbox-lo-1.0.0.tar.gz
docker load -i toolbox-lo-1.0.0.tar

# 3. 验证
docker run --rm toolbox-base:1.0 soffice --version
docker run --rm toolbox-base:1.0 fc-list :lang=zh | head -10

# 4. 启动
docker run -d --name toolbox -p 8899:8899 --restart unless-stopped toolbox-lo:1.0.0
```

---

## 三、后续更新 — 只传应用镜像

改代码后只需更新应用层：

```bash
# 本地
cd frontend && npm run build && cd ..
cd backend && mvn clean package -DskipTests && cd ..
docker build -t toolbox-lo:1.0.1 .                              # 秒级
docker save -o toolbox-lo-1.0.1.tar toolbox-lo:1.0.1
gzip toolbox-lo-1.0.1.tar
scp toolbox-lo-1.0.1.tar.gz root@server:/opt/images/

# 服务器
cd /opt/images/
gunzip toolbox-lo-1.0.1.tar.gz
docker load -i toolbox-lo-1.0.1.tar
docker stop toolbox && docker rm toolbox
docker run -d --name toolbox -p 8899:8899 --restart unless-stopped toolbox-lo:1.0.1
```

基础镜像 `toolbox-base:1.0` 不需要更新，服务器上一直保留。

---

## 四、字体清单

| 字体包 | 类型 | 风格 |
|--------|------|------|
| `fonts-noto-cjk` | 思源黑体+宋体 | 常规字重，主力 |
| `fonts-wqy-microhei` | 文泉驿微米黑 | 无衬线黑体 |
| `fonts-wqy-zenhei` | 文泉驿正黑 | WPS 兼容 |
| `fonts-arphic-ukai` | 文鼎楷体 | 楷书 |
| `fonts-arphic-uming` | 文鼎明体 | 宋体/明体 |
| `fonts-liberation` | Liberation | Times/Arial/Courier 等价 |

---

## 五、日常运维

```bash
docker logs -f toolbox
docker exec toolbox soffice --version     # 验证 LO
docker exec toolbox fc-list :lang=zh      # 验证字体
docker restart toolbox
docker stats toolbox
```
