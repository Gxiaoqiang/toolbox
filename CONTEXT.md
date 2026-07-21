# CONTEXT.md — Toolbox 领域词汇表

## 核心概念

### 工具（Tool）
系统提供的单一功能单元。每个工具对应一个前端组件和可选的后端服务。
- 属性：`id`（唯一标识）、`name`（显示名称）、`category`（分类）、`group`（分组）
- 分类：`file`（文件处理）、`develop`（开发工具）、`data`（数据处理）

### 文件处理工具（File Tool）
以文件为输入/输出的工具。前端采用"左输入 → 中按钮 → 右结果"三栏布局。
- 输入：用户上传文件或提供 URL
- 输出：文件下载（PDF、ZIP 等）

### PDF 工具包
一组以 PDF 为处理对象的工具集合，包括：切分、合并、压缩、加密、转图片、图片转 PDF、文档转 PDF、HTML 转 PDF。

---

## HTML 转 PDF 领域

### 渲染引擎（Render Engine）
将 HTML 内容转换为 PDF 的底层技术。本项目使用 **Playwright for Java**（Chromium 内核）。

### 渲染上下文（Render Context）
一次 HTML 转 PDF 任务的完整参数集合，包括：
- **输入源（Input Source）**：URL 或本地 HTML 文件
- **视口（Viewport）**：Chromium 渲染时的窗口尺寸（桌面 1280px / 平板 768px / 手机 375px）
- **纸张设置（Paper Settings）**：纸张大小（A4/Letter/Legal）、方向（纵向/横向）
- **边距（Margins）**：页面四周留白（无/窄/中/宽/自定义）
- **渲染行为（Render Behavior）**：JS 执行开关、等待策略（networkidle）、超时时间
- **输出设置（Output Settings）**：缩放比例、背景图形开关、页眉页脚

### 广告过滤（Ad Filtering）
在渲染前移除或隐藏网页中的广告内容。两层机制：
- **域名拦截（Domain Blocking）**：通过 Playwright 的 `route.abort()` 拦截已知广告域名的请求
- **元素隐藏（Element Hiding）**：通过注入 CSS 选择器隐藏页面中的广告元素

### 预览（Preview）
用户在转换前查看输入内容的前端行为：
- **URL 预览**：通过 `<iframe :src="url">` 嵌入目标网页
- **文件预览**：通过 `<iframe srcdoc="htmlContent">` 渲染本地 HTML

### 并发限制（Concurrency Limit）
同一时刻只允许一个渲染任务运行，防止 Chromium 实例过多导致服务器内存溢出。

---

## 通用术语

### 统一响应体（R<T>）
后端 API 的标准返回格式：`{ code: number, message: string, data: T }`。

### 错误码（ErrorCodeEnum）
业务异常的枚举定义，格式为 `模块_操作_错误类型`，如 `HTML_TO_PDF_RENDER_TIMEOUT`。

### 无状态部署（Stateless Deployment）
每个服务器实例独立运行，不共享会话状态。通过负载均衡分发请求，支持水平扩展。
