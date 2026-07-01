# 🧰 Toolbox — 开发/办公工具箱

> 一个可扩展的 Web 工具箱，集成 JSON 处理、Markdown 转换、编解码、哈希计算等常用工具。

## 在线体验

启动后访问 `http://localhost:8899`

## 功能清单（11 个工具）

### 📄 文档工具

| 工具 | 说明 |
|------|------|
| **Markdown 工具箱** | Markdown 实时预览（GFM）、导出 HTML、导出 DOCX |

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
| 前端 | Vue 3 (Composition API) + TypeScript + Vite + TailwindCSS |
| 后端 | Spring Boot 3.3 + JDK 17 + Maven |
| Markdown | marked (GFM) / flexmark (Java) |
| DOCX | docx4j (AltChunk 嵌入 HTML) |
| MD5 | spark-md5 |
| 数据库 | MySQL 8.0（预留，当前无需） |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20+（仅开发构建时需要）

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

## 项目结构

```
toolbox/
├── frontend/                  # Vue 3 前端
│   └── src/
│       ├── tools/             # ★ 工具组件（约定式自动注册）
│       │   ├── types.ts       # ToolMeta 接口定义
│       │   ├── registry.ts    # 自动扫描 & 注册中心
│       │   ├── md-toolbox/    # Markdown 工具箱
│       │   ├── json-formatter/# JSON 工具箱
│       │   └── ...            # 其他工具
│       ├── layouts/           # 布局组件
│       ├── router/            # 路由配置
│       └── composables/       # 通用组合式函数
├── backend/                   # Spring Boot 后端
│   └── src/main/java/com/toolbox/
│       ├── controller/        # 接口层
│       ├── service/           # 业务层
│       ├── model/             # 数据模型（R 统一响应体）
│       ├── exception/         # 全局异常处理
│       └── config/            # Web 配置
└── docs/superpowers/          # 设计文档
    ├── specs/                 # 需求规范
    └── plans/                 # 实现计划
```

## 扩展新工具

只需 3 步，零配置：

1. 在 `frontend/src/tools/` 下新建文件夹 `my-tool/`
2. 创建 `index.vue`，导出 `meta` 对象：

```typescript
const meta: ToolMeta = {
  id: 'my-tool',
  name: '我的工具',
  description: '工具描述',
  category: 'develop',          // document | develop | data
  group: '可选分组',             // 同一分类下聚合展示
  requiresBackend: false,       // 是否需要后端
}
```

3. 完成 — 菜单和路由自动生成。

## 后端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/convert/md-to-docx` | Markdown 转 DOCX 文件下载 |

## 编码规范

- **前端**: Vue 3 官方风格指南 + ESLint + Prettier
- **后端**: 阿里巴巴 Java 开发手册（嵩山版）

## License

MIT
