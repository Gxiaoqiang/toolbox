# 🧰 Toolbox — 开发/办公工具箱

> 一个可扩展的 Web 工具箱，集成 JSON 处理、Markdown 转换、PDF 切分、文档转 PDF、编解码、哈希计算等常用工具。

## 在线体验

启动后访问 `http://localhost:8899`

## 功能清单（13 个工具）

### 📄 文件工具

| 工具 | 说明 | 后端 |
|------|------|------|
| **Markdown 工具箱** | Markdown 实时预览（GFM）、快捷插入、语法速查、导出 HTML、导出 DOCX | ✓ |
| **PDF 切分** | 逐页拆分 / 按页码范围（如 1,3,5-8）/ 每 N 页拆分，支持保留元数据，ZIP 下载 | ✓ |
| **文档转 PDF** | .doc / .docx / .wps → PDF 批量转换（最多 5 个），ZIP 下载 | ✓ |

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
| Markdown | marked (GFM 前端渲染) / flexmark + docx4j (服务端 DOCX 导出) |
| PDF 处理 | Apache PDFBox 3.0 |
| 文档转换 | LibreOffice headless (soffice CLI) |
| 其他 | js-yaml, spark-md5 |

## 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20+（仅开发构建时需要）
- LibreOffice（仅文档转 PDF 功能需要，非必需）

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
│       │   ├── doc-to-pdf/    # 文档转 PDF
│       │   ├── json-formatter/# JSON 工具箱
│       │   └── ...            # 其他工具
│       ├── layouts/           # 布局组件
│       ├── router/            # 路由配置
│       └── composables/       # 通用组合式函数
├── backend/                   # Spring Boot 后端
│   └── src/main/java/com/toolbox/
│       ├── controller/        # 接口层
│       │   ├── markdown/      # Markdown 转换接口
│       │   ├── pdf/           # PDF 切分接口
│       │   └── document/      # 文档转 PDF 接口
│       ├── service/           # 业务层
│       ├── model/             # 数据模型（R 统一响应体）
│       ├── exception/         # 全局异常处理
│       ├── util/              # 工具类
│       └── config/            # Web 配置
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
| POST | `/api/document/convert-to-pdf` | 文档转 PDF（批量最多 5 个），ZIP 下载 |

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
