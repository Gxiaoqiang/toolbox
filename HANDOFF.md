# HANDOFF — 会话交接文档

> 日期: 2026-07-12
> 上一会话总结，写给零上下文的新会话

---

## 一、项目概览

**Toolbox** — 开发/办公工具箱，Web 端工具集。

- 前端: Vue 3 + TypeScript + Vite + TailwindCSS v4
- 后端: Spring Boot 3.3 + JDK 17 + Maven
- 部署: 单 JAR（内嵌前端静态资源），端口 8899

---

## 二、本会话完成的任务

### 1. PDF 合并功能（新功能）

**后端**:
- `PdfService.mergePdf()` — PDFBox 逐页复制实现合并，支持加密检测、元数据保留
- `POST /api/pdf/merge` — 接收 2-10 个 PDF 文件，返回单个 merged.pdf
- 参数校验: 扩展名 .pdf、≤5MB、2-10 个文件、加密文件中断并报错
- 42 个后端测试全部通过

**前端** (`frontend/src/tools/pdf-merge/index.vue`):
- 三栏布局: 左（虚线上传区+文件列表）| 中（合并按钮）| 右（虚线结果框）
- 文件列表支持 HTML5 拖拽排序（⋮⋮ 手柄），显示序号+文件名+页数+大小
- 与 pdf-splitter/doc-to-pdf 风格统一（虚线框、SVG 加载动画、按钮状态切换）

### 2. 文档转 PDF 页面 UI 修复

修复了 `doc-to-pdf/index.vue` 的 4 个问题:
- 虚线拖拽上传区选中文件后不再消失（始终可见，缩小为 h-24）
- 按钮"转换"文字不换行（列宽 80px + whitespace-nowrap + 竖向排列）
- 加载动画从文字符号 `⟳` 替换为 22×22px SVG 旋转圆环
- 按钮在 processing 时保持可见但置灰不可点击

### 3. README + 项目清理

- README 更新: 13 个工具、正确的分类名、API 路径、主题系统说明
- `docs/superpowers/` 下的 6 个设计过程文件从 git 跟踪中移除（已在 .gitignore）

### 4. 分布式部署 brainstorming（未实施，仅设计讨论）

讨论了方案 B（服务拆分 + 路径路由），未写入代码:
- light pool: Markdown + PDF 切分，无需 LibreOffice
- heavy pool: 文档转 PDF，需 LibreOffice
- 同一份 JAR，通过 `spring.profiles.active` 控制装配
- Nginx 按 `/api/document/*` 路径分流

---

## 三、当前项目文件结构

```
frontend/src/tools/
├── types.ts              # ToolMeta 接口: id, name, category, requiresBackend
├── registry.ts           # import.meta.glob 自动扫描，mod.meta 优先
├── md-toolbox/           # Markdown 工具箱 (category: file)
├── pdf-splitter/         # PDF 切分 (category: file)
├── pdf-merge/            # PDF 合并 (category: file) ★ 新增
├── doc-to-pdf/           # 文档转 PDF (category: file)
├── json-formatter/       # JSON 工具箱 (category: develop)
├── ...                   # 其他开发辅助/数据处理工具

backend/src/main/java/com/toolbox/
├── controller/
│   ├── markdown/MarkdownController.java   # POST /api/markdown/md-to-docx
│   ├── pdf/PdfController.java             # POST /api/pdf/split, /api/pdf/merge
│   └── document/DocumentController.java   # POST /api/document/convert-to-pdf
├── service/
│   ├── markdown/   (MarkdownService + Impl)
│   ├── pdf/        (PdfService + Impl)
│   └── document/   (DocumentService + Impl — 依赖 LibreOffice headless)
├── exception/      (BusinessException, ErrorCodeEnum, GlobalExceptionHandler)
├── model/common/R.java   # 统一响应体 {code, message, data}
└── util/FileTypeValidator.java
```

---

## 四、绝对不要踩的坑

### 1. Vue 组件 meta 导出
**必须**使用独立 `<script lang="ts">` 块 + 模块级命名导出，`<script setup>` 中的 `const meta` 不是模块导出:
```vue
<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = { id: 'xxx', name: 'xxx', category: 'file', ... }
</script>
```
否则 registry.ts 读到 `component.meta === undefined`，工具回退到默认 `category: 'develop'`，菜单不显示。

### 2. PDFBox 3.x API
- 加载 PDF: `Loader.loadPDF(bytes)`，不是 `PDDocument.load()`
- 逐页复制: `new PDPage(source.getPage(i).getCOSObject())`，不能直接 add 原页面引用
- `PDFMergerUtility.addSource()` 在 3.x 不接受 PDDocument，不接受 MemoryUsageSetting。**合并请用逐页复制方案**，不要尝试 PDFMergerUtility
- `setDocumentInformation(ref)` 在 3.0 无效，必须逐字段复制 title/author/subject 等

### 3. 构建产物目录
- 前端 build 产物输出到 `../backend/src/main/resources/static/`
- 提交时必须包含 static 目录的 hash 文件变更（文件名 hash 每次构建不同）
- 后端打包前必须先 `cd frontend && npm run build`

### 4. LibreOffice 依赖
- DocumentService 调用 `soffice --headless`（路径可通过 `toolbox.libreoffice.binary-path` 配置，默认 `soffice`）
- 没有 soffice 时 `isServiceAvailable()` 返回 false，API 返回 500
- Docker 镜像基于 `eclipse-temurin:17-jre-jammy`（Ubuntu 22.04），已内置 LibreOffice + 丰富中文字体
  - **中文字体**（6 款）: Noto CJK 全字重、文泉驿微米黑/正黑、文鼎楷体、文鼎明体、花園明朝
  - **西文字体**（2 款）: Liberation、DejaVu
  - **不要用 Alpine 安装 LibreOffice**：Alpine 版本缺少 .wps 过滤器且 CJK 渲染有问题
- 离线部署: `build-docker.sh` 一键构建 → `docker save` → tar.gz 传输 → 服务器 `docker load`
- 详细文档: `DEPLOY-OFFLINE.md`

### 5. 测试中的加密 PDF
- `setAllSecurityToBeRemoved(true)` **不会**创建加密 PDF
- 创建真正加密的 PDF 需要用 `StandardProtectionPolicy` + `doc.protect(spp)`

### 6. 文件校验参数
各工具的校验参数不同，**不要混淆**:

| 工具 | 最多文件 | 单文件上限 | 允许格式 |
|------|---------|-----------|---------|
| PDF 切分 | 1 | 50MB | .pdf |
| PDF 合并 | 10 | 5MB | .pdf |
| 文档转 PDF | 5 | 50MB | .doc/.docx/.wps |

### 7. 全局异常处理器
`GlobalExceptionHandler` 处理 `BusinessException` 时会根据 error code 返回 400 或 500，**不要**改回始终 200。

---

## 五、本会话完成的任务（2026-07-13）

### 5. Dockerfile 改造 + soffice 配置化 + 离线部署方案

- Dockerfile 切换基础镜像: `eclipse-temurin:17-jre-alpine` → `eclipse-temurin:17-jre-jammy`
- 安装 `libreoffice-writer` + 6 款中文字体 + 2 款西文字体（黑体/宋体/楷体/明体全覆盖）
- `application.yml` 新增 `toolbox.libreoffice.binary-path: soffice` 配置项
- `DocumentServiceImpl` 构造函数注入 soffice 路径（`@Value`），替代硬编码 `"soffice"`
- 新增 `build-docker.sh` 一键构建导出脚本
- 新增 `DEPLOY-OFFLINE.md` 完整离线部署指南
- **注意**: 当前开发机 Docker 镜像站（阿里云 403）不可用，需修复后构建镜像

---
## 六、当前卡点

### Shell CWD 持久化问题（开发效率）

Shell 的 `CWD` 在连续调用间会保留上次的位置，导致命令意外失败：
- `cd frontend && npm run build` 后直接调 `mvn` → 在 frontend/ 找不到 POM
- `cd backend && mvn` 后直接调 `npm` → 在 backend/ 找不到 package.json
- `pkill` + `java -jar` 后 CWD 可能仍在前端目录

**Workaround**: 每个涉及跨目录的 Bash 调用都用绝对路径 `cd`：
```bash
cd /Users/.../frontend && npm run build
cd /Users/.../backend && mvn clean package -DskipTests
```

### Docker 镜像构建

开发机 Docker 镜像站不可用（阿里云 403），base 镜像暂未构建。部署到离线服务器时需在有网络的环境先 `docker build` + `docker save`。

---

## 七、本会话完成的任务（2026-07-14）

### 1. PDF 压缩功能（新功能）★

**后端**（5 个文件）:

| 文件 | 说明 |
|------|------|
| `service/pdf/PdfCompressConstant.java` | 常量 + `CompressLevel` 枚举，5 档参数（label/description/targetDpi/jpegQuality/removeMeta） |
| `service/pdf/PdfCompressService.java` | 接口：`compress(bytes, filename, level) → PdfCompressResult` |
| `service/pdf/PdfCompressResult.java` | DTO：`data` + `originalSize` + `compressedSize` + `getCompressionRatio()` |
| `service/pdf/impl/PdfCompressServiceImpl.java` | 核心实现，串行渲染（PDDocument 非线程安全） |
| `controller/pdf/PdfCompressController.java` | 薄层：校验 + 委托，响应头 `X-Original-Size` / `X-Compressed-Size` |

**压缩策略**: 逐页遍历 `PDResources.getXObjectNames()` → 找到 `PDImageXObject` → 基于页面 mediaBox 和目标 DPI 计算降采样比例 → Bicubic 缩放 + `JPEGFactory.createFromImage()` 重编码 → 替换资源引用。元数据清理（低压缩等级时移除文档信息和 XMP 流）。

**5 档压缩等级**:

| 等级 | 标签 | DPI | JPEG 质量 | 元数据 |
|------|------|-----|-----------|--------|
| 1 | 极度压缩 | 72 | 0.4 | 移除 |
| 2 | 高度压缩 | 100 | 0.55 | 移除 |
| 3 | **推荐压缩**（默认） | 150 | 0.7 | 保留 |
| 4 | 轻度压缩 | 200 | 0.85 | 保留 |
| 5 | 极限画质 | 300 | 0.95 | 保留 |

**API**: `POST /api/pdf/compress`，参数 `file` + `level`(1-5)，返回 `application/pdf`

**错误码新增**: `PDF_COMPRESS_LEVEL_INVALID`(400)、`PDF_COMPRESS_PROCESS_ERROR`(500)

**前端** (`frontend/src/tools/pdf-compress/index.vue`):
- 三栏布局: 左（上传区+等级选择器）| 中（压缩按钮）| 右（压缩结果）
- 等级选择器: 双列网格 `grid-cols-2`，5 张紧凑卡片，无需滚动
- hover 浮层: `group-hover` 显示完整等级描述（CSS 深底 tooltip + 三角箭头）
- 压缩率可视化: 绿色/黄色渐变进度条 + 百分比数字
- 状态机: `noFile → ready → processing → done/error`（processing 时文件不可移除、等级不可切换）

### 2. 侧边栏 PDF 工具包分组 ★

**4 个 PDF 工具**（pdf-splitter, pdf-merge, pdf-to-image, pdf-compress）统一加上 `group: 'PDF 工具包'`。

**MainLayout.vue 改造**:
- 分组标题改为**可折叠手风琴**: 点击 `▶ 📑 PDF 工具包` 展开/收起内部工具
- 三级缩进层级:
  - 一级分类（`📄 文件工具`）: `px-2` (8px)，颜色 `var(--text-secondary)`
  - 二级分组/无分组工具: `pl-6` (24px)，**同缩进**（平级关系）
  - 三级组内工具（`· PDF 切分` 等）: `pl-9` (36px)
- 分组标题配色: `GROUP_COLORS` 映射表，PDF 工具包用 `#6366f1`（indigo），与分类标题颜色区分
- 图标: `📑`（书签页），与分类图标 `📄` 区分

**侧边栏样式规范已记忆**: `memory/sidebar-menu-pattern.md`，后续新增分组直接参照。

### 3. 前端依赖
- PDF 压缩页面体积: ~11KB gzip，在 109KB 的总 bundle 中

---

## 八、当前项目结构（更新）

```
frontend/src/tools/
├── pdf-splitter/         # PDF 切分     [group: PDF 工具包]
├── pdf-merge/            # PDF 合并     [group: PDF 工具包]
├── pdf-to-image/         # PDF 转图片   [group: PDF 工具包]
├── pdf-compress/         # PDF 压缩 ★   [group: PDF 工具包]
├── doc-to-pdf/           # 文档转 PDF   (无分组)
├── md-toolbox/           # Markdown 工具箱
├── json-formatter/       # JSON 工具箱
├── ...                   # 其他 10 个工具

backend/src/main/java/com/toolbox/
├── controller/pdf/
│   ├── PdfController.java           # split、merge
│   ├── PdfToImageController.java    # to-image
│   └── PdfCompressController.java   # compress ★
├── service/pdf/
│   ├── PdfService.java + Impl       # split、merge
│   ├── PdfToImageService.java + Impl   # to-image
│   ├── PdfToImageResult.java
│   ├── ImageConvertConstant.java    # DPI/格式常量 + ImageFormat 枚举
│   ├── PdfCompressService.java + Impl  # compress ★
│   ├── PdfCompressResult.java       # ★
│   └── PdfCompressConstant.java     # CompressLevel 枚举 ★
├── config/ThreadPoolConfig.java     # 全局线程池（串行渲染未使用，预留）
└── exception/ErrorCodeEnum.java     # 20 个错误码（新增 2 个压缩相关）
```

---

## 九、快速启动命令

```bash
# 开发前端
cd frontend && npm run dev          # :3000

# 全量构建 + 启动
cd frontend && npm run build
cd /Users/.../backend && mvn clean package -DskipTests
java -jar target/toolbox-1.0.0.jar  # :8899

# 运行测试
cd /Users/.../backend && mvn test   # 42 tests, 0 failures

# Docker 构建（有网络环境）
docker build -t toolbox-lo:1.0.0 .
docker save -o toolbox-lo-1.0.0.tar toolbox-lo:1.0.0
gzip toolbox-lo-1.0.0.tar
```
