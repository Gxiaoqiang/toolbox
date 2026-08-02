
# 🧰 Toolbox — 开发/办公工具箱

> 一个可扩展的 Web 工具箱，集成文档转换、PDF 处理、图片转 PDF、JSON 处理、编解码、哈希计算等 20+ 常用工具。内置 AI 文档助手，支持自然语言操作。

## 在线体验

启动后访问 `http://localhost:8899`

## 功能清单（24 个工具）

### 📄 文档转换

| 工具 | 说明 | 后端 |
|------|------|------|
| **Markdown 工具箱** | Markdown 实时预览（GFM）、快捷插入、语法速查、导出 HTML、导出 DOCX | ✓ |
| **文档转 PDF** | .doc / .docx / .wps → PDF 批量转换（最多 5 个），ZIP 下载 | ✓ |
| **HTML 转 PDF** | 网页 URL 或本地 HTML 文件 → PDF，Playwright 完整渲染 | ✓ |

### 📑 PDF 处理

| 工具 | 说明 | 后端 |
|------|------|------|
| **PDF 切分** | 逐页拆分 / 按页码范围（如 1,3,5-8）/ 每 N 页拆分，支持保留元数据，ZIP 下载 | ✓ |
| **PDF 合并** | 多个 PDF 按顺序合并为一个（2-10 个），支持保留元数据 | ✓ |
| **PDF 压缩** | 5 级压缩（极度压缩 → 极限画质），显示压缩比 | ✓ |
| **PDF 转图片** | PDF 逐页转 PNG/JPEG/WEBP，可调 DPI（72-600），ZIP 下载 | ✓ |
| **PDF 编排** | 自由编排 PDF 页面——拖拽排序、删除、旋转、插入空白页、合并多文件 | ✓ |
| **PDF 加密** | 添加密码保护，支持用户密码和所有者密码，控制打印/复制/修改权限 | ✓ |
| **PDF 涂黑遮盖** | 拖拽绘制方块遮盖敏感内容，支持标准/深度两种模式 | ✓ |
| **PDF 去水印** | 框选水印区域，删除框内文字/图片水印，保留正文（矢量无损），支持应用到所有页 | ✓ |
| **PDF 添加水印** | 添加文字/图片水印，自定义字体/字号/颜色/旋转/透明度/位置/页面范围，支持实时预览与撤销重做 | ✓ |

### 🖼️ 图片处理

| 工具 | 说明 | 后端 |
|------|------|------|
| **图片转 PDF** | 多张图片合并为 PDF，支持 JPG/PNG/WEBP/GIF，可调方向/边距/适配方式 | ✓ |

### 🤖 AI 助手

| 工具 | 说明 | 后端 |
|------|------|------|
| **文档助手** | AI 对话式处理，自然语言完成 PDF/文档/图片操作，支持文件上传 | ✓ |

### 💻 开发辅助

| 工具 | 说明 |
|------|------|
| **JSON 工具箱** | 格式化/校验/转义、交互式树形查看、JSONPath 查询、JSON ↔ XML 互转 |
| **YAML 格式化** | YAML ↔ JSON 互转 |
| **JWT 解码** | JWT Token 解码，查看 Header/Payload/过期时间 |
| **正则测试** | 正则表达式实时匹配高亮 |
| **Base64 编解码** | Base64 编码/解码（UTF-8） |
| **Diff 对比** | 文本逐行差异对比 |
| **URL 编解码** | URL encodeURIComponent 编码与解码 |
| **哈希计算** | MD5 / SHA-1 / SHA-256 / SHA-512，支持加盐 |
| **时间戳转换** | Unix 时间戳与日期互转 |

### 📊 数据处理

| 工具 | 说明 |
|------|------|
| **数据处理** | CSV ↔ JSON 互转、去重、排序 |

## 技术栈

| 层 | 技术 |
|---|------|
| 前端 | Vue 3 (Composition API) + TypeScript + Vite + TailwindCSS v4 |
| 后端 | Spring Boot 3.3 + JDK 17 + Maven |
| AI 助手 | AgentScope (ReAct Agent) + DeepSeek / DashScope LLM |
| Markdown | marked (GFM 前端渲染) / flexmark + docx4j (服务端 DOCX 导出) |
| PDF 处理 | Apache PDFBox 3.0 / pdfjs-dist (前端缩略图渲染) |
| 文档转换 | LibreOffice headless (soffice CLI) |
| 其他 | js-yaml, spark-md5 |

## 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20+（仅开发构建时需要）
- LibreOffice（仅文档转 PDF 功能需要，非必需）
- Redis（可选，用于 Agent 会话持久化，默认使用内存存储）

## 快速开始

### 开发模式

```bash
# 1. 启动后端
cd backend
mvn spring-boot:run

# 2. 启动前端（另一个终端）
cd frontend
npm install
npm run dev
# 前端 dev server: http://localhost:3000
```

### 生产构建

```bash
# 构建前端（产物输出到 backend/src/main/resources/static/）
cd frontend
npm install
npm run build

# 打包后端（含前端静态资源）
cd ../backend
mvn clean package -DskipTests

# 运行
java -jar target/toolbox-1.0.0.jar
# 浏览器打开 http://localhost:8899
```

### Docker 部署

```bash
# 构建镜像（确保已执行过 mvn package）
docker build -t toolbox:1.0.0 .

# 启动容器
docker run -d -p 8899:8899 --name toolbox toolbox:1.0.0

# 查看日志
docker logs -f toolbox
```

## LibreOffice 安装（文档转 PDF 必需）

文档转 PDF 功能依赖 LibreOffice headless 模式。不安装时其他功能正常使用，仅「文档转 PDF」不可用。

### macOS

```bash
brew install --cask libreoffice
# 安装后 soffice 命令自动可用
```

### Ubuntu / Debian

```bash
sudo apt update
sudo apt install -y libreoffice-writer
# 仅安装 writer 组件，体积最小
```

### CentOS / RHEL

```bash
sudo yum install -y libreoffice-writer libreoffice-langpack-zh-CN
```

### Windows

1. 下载安装包：https://www.libreoffice.org/download/
2. 安装时勾选「自定义安装」→ 仅安装 Writer
3. 将安装目录加入 PATH，或在 `application.yml` 中配置完整路径：

```yaml
toolbox:
  libreoffice:
    binary-path: C:\Program Files\LibreOffice\program\soffice.exe
```

### 验证安装

```bash
soffice --version
# 预期输出类似: LibreOffice 7.x.x.x xxx
```

### Docker 环境

Dockerfile 已内置 LibreOffice + 中文字体（Noto CJK + WQY），无需额外安装。

## AI 文档助手配置

文档助手需要配置 LLM API Key 才能使用。支持三种 LLM 提供商：

> **本地运行**：默认启用 `local` profile，会读取（gitignore 的）`backend/src/main/resources/application-local.yml` 中的本地密钥，无需每次设置环境变量；生产/CI 请用环境变量 `LLM_API_KEY` 覆盖，勿提交明文密钥。

### 环境变量配置

```bash
# DeepSeek（推荐，性价比高）
export LLM_API_KEY=sk-your-deepseek-api-key

# 或者使用阿里云百炼（DashScope）
export LLM_API_KEY=sk-your-dashscope-api-key

# 可选：自定义 API Base URL（内网代理等）
export LLM_BASE_URL=https://your-proxy.example.com
```

### application.yml 配置

```yaml
toolbox:
  agent:
    # LLM 提供商: dashscope / openai / deepseek
    llm-provider: deepseek
    # 模型名称
    llm-model: deepseek-v4-pro
    # API Key（优先使用环境变量 LLM_API_KEY）
    llm-api-key: ${LLM_API_KEY:}
    # API Base URL（可选）
    llm-base-url: ${LLM_BASE_URL:}
```

### 支持的 LLM 提供商

| 提供商 | llm-provider | llm-model 推荐 | 说明 |
|--------|-------------|---------------|------|
| DeepSeek | `deepseek` | `deepseek-v4-pro` | 推荐，性价比高 |
| 阿里云百炼 | `dashscope` | `qwen-plus` | 国内访问快 |
| OpenAI | `openai` | `gpt-4o` | 需要海外网络 |

### 会话存储（可选）

默认使用内存存储会话，重启后清空。配置 Redis 可持久化：

```bash
export TOOLBOX_STORE_CONVERSATION=redis
export TOOLBOX_STORE_CONNECTION=redis
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

## 配置说明

应用核心配置集中在 `backend/src/main/resources/application.yml`，常用项均支持环境变量覆盖。

### 服务器与上传

```yaml
server:
  port: ${SERVER_PORT:8899}          # 服务端口，可用环境变量 SERVER_PORT 覆盖

spring:
  servlet:
    multipart:
      max-file-size: 50MB            # 单文件上传上限
      max-request-size: 50MB         # 单次请求总大小上限
```

### 存储类型

`toolbox.store.*` 决定文件/会话/连接的存储后端，可用 `TOOLBOX_STORE_XXX` 环境变量覆盖：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `toolbox.store.file-store` | `local` | 文件存储：`local` / `oss` |
| `toolbox.store.conversation-store` | `redis` | 会话存储：`local` / `redis` |
| `toolbox.store.connection-registry` | `redis` | 连接注册：`local` / `redis` |

> Redis 仅在需要时启用（`TOOLBOX_STORE_CONVERSATION=redis` 或 `TOOLBOX_STORE_CONNECTION=redis` 时）。

### LibreOffice（文档转 PDF）

```yaml
toolbox:
  libreoffice:
    binary-path: soffice             # soffice 二进制路径
    max-concurrent: 10               # 最大并发转换数（每进程约占 200-500MB 内存）
```

### Playwright（HTML/URL 转 PDF）

```yaml
toolbox:
  playwright:
    max-concurrent: ${TOOLBOX_PLAYWRIGHT_MAX_CONCURRENT:2}  # 最大并发 Chromium 实例数（每个约 500MB）
```

### 安全防控（限流 / URL 防护 / 文件校验）

```yaml
toolbox:
  security:
    rate-limit:
      enabled: true                      # 限流总开关
      store: ${TOOLBOX_RATE_LIMIT_STORE:redis}  # 限流存储后端: local / redis
      default-permits-per-second: 5.0    # 未标注 @RateLimit 的接口默认每秒令牌数
      default-burst: 10                  # 默认令牌桶容量（允许的最大突发）
    url-protection:
      enabled: true                      # URL 安全防护
      block-private-ips: true            # 阻止访问内网/私有 IP（SSRF 防护）
    file-validation:
      check-magic-bytes: true            # 文件魔数校验（校验实际内容而非仅扩展名）
```

- 单个接口可用 `@RateLimit(permitsPerSecond, burst, tier)` 注解覆盖全局默认限流（如 PDF 处理接口为 `3.0/s`，突发 `8`）。
- `store: local` 为单机内存限流；`redis` 为分布式限流（需配置 Redis）。

### Agent（AI 文档助手）

详见上文「AI 文档助手配置」章节。核心环境变量：`LLM_API_KEY`、`LLM_MODEL`、`LLM_BASE_URL`。

## 项目结构

```
toolbox/
├── frontend/                  # Vue 3 前端
│   └── src/
│       ├── tools/             # ★ 工具组件（约定式自动注册）
│       │   ├── types.ts       # ToolMeta 接口定义
│       │   ├── registry.ts    # 自动扫描 & 注册中心
│       │   ├── md-toolbox/    # Markdown 工具箱
│       │   ├── pdf-splitter/  # PDF 切分
│       │   ├── pdf-merge/     # PDF 合并
│       │   ├── pdf-compress/  # PDF 压缩
│       │   ├── pdf-to-image/  # PDF 转图片
│       │   ├── pdf-arrange/   # PDF 编排
│       │   ├── image-to-pdf/  # 图片转 PDF
│       │   ├── doc-to-pdf/    # 文档转 PDF
│       │   ├── doc-agent/     # AI 文档助手
│       │   ├── json-formatter/# JSON 工具箱
│       │   └── ...            # 其他工具
│       ├── layouts/           # 布局组件
│       ├── router/            # 路由配置
│       └── composables/       # 通用组合式函数
├── backend/                   # Spring Boot 后端
│   └── src/main/java/com/toolbox/
│       ├── controller/        # 接口层
│       │   ├── markdown/      # Markdown 转换接口
│       │   ├── pdf/           # PDF 处理接口（切分/合并/压缩/转图片/编排）
│       │   ├── image/         # 图片处理接口（图片转 PDF）
│       │   ├── document/      # 文档转 PDF 接口
│       │   └── AgentController.java  # AI 文档助手接口
│       ├── service/           # 业务层
│       │   ├── pdf/           # PDF 处理服务
│       │   ├── image/         # 图片处理服务
│       │   ├── agent/         # Agent 工具箱 + 会话管理
│       │   ├── document/      # 文档转换服务
│       │   ├── markdown/      # Markdown 服务
│       │   └── store/         # 文件存储
│       ├── model/             # 数据模型（R 统一响应体）
│       ├── exception/         # 全局异常处理
│       ├── util/              # 工具类
│       └── config/            # Web + Agent 配置
└── Dockerfile
```

## 扩展新工具

只需 3 步，零配置：

1. 在 `frontend/src/tools/` 下新建文件夹 `my-tool/`
2. 创建 `index.vue`，导出 `meta` 对象：

```typescript
// 注意：meta 必须使用 <script lang="ts"> 独立块导出（模块级命名导出）
const meta: ToolMeta = {
  id: 'my-tool',
  name: '我的工具',
  description: '工具描述',
  category: 'file',               // file | develop | data
  requiresBackend: false,         // 是否需要后端支持
}
```

3. 完成 — 菜单和路由自动生成。

## 后端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/markdown/md-to-docx` | Markdown 转 DOCX 文件下载 |
| POST | `/api/pdf/split` | PDF 切分（逐页/范围/每N页），ZIP 下载 |
| POST | `/api/pdf/merge` | PDF 合并（2-10 个文件） |
| POST | `/api/pdf/compress` | PDF 压缩（5 级） |
| POST | `/api/pdf/to-image` | PDF 转图片（PNG/JPEG/WEBP） |
| POST | `/api/pdf/arrange` | PDF 编排（页面排序/删除/旋转/插入空白页） |
| POST | `/api/image/to-pdf` | 图片转 PDF（JPG/PNG/WEBP/GIF） |
| POST | `/api/document/convert-to-pdf` | 文档转 PDF（批量最多 5 个），ZIP 下载 |
| POST | `/api/pdf/url-to-pdf` | URL 网页 → PDF |
| POST | `/api/pdf/file-to-pdf` | HTML 文件 → PDF |
| GET  | `/api/pdf/preview-html` | 网页 URL 预览截图（Playwright） |
| POST | `/api/pdf/encrypt` | PDF 加密（密码 + 权限控制） |
| POST | `/api/pdf/redact` | PDF 涂黑遮盖（标准/深度） |
| POST | `/api/pdf/dewatermark` | PDF 去水印（框选删除水印，返回区域级结果） |
| POST | `/api/pdf/watermark` | PDF 添加水印（文字/图片，自定义样式/位置/页面范围） |
| POST | `/api/agent/chat` | AI 文档助手对话（SSE 流式响应） |

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

## 主题系统

5 套主题通过 CSS 自定义属性切换，localStorage 持久化：

| 主题 | 说明 |
|------|------|
| 护眼绿（默认） | 柔和绿色背景，舒适护眼 |
| 默认白 | 经典白色背景 |
| 暖色奶油 | 温暖奶油色调 |
| 深色暗夜 | 深色暗夜模式 |
| 浅灰柔白 | 浅灰柔白配色 |

## 编码规范

- **前端**: Vue 3 官方风格指南 + ESLint + Prettier
- **后端**: 阿里巴巴 Java 开发手册（嵩山版）

## License

MIT
