# Spec: 图片转 PDF

**日期**: 2026-07-19
**状态**: ready-for-agent
**标签**: `ready-for-agent`

---

## Problem Statement

用户有一组图片（扫描件、照片、截图等），需要将其合并为 PDF 文件以便归档、打印或分享。目前工具箱没有图片→PDF 的能力，用户只能借助外部工具完成。同时，文档助手（Agent）也需要具备此能力，让用户通过自然语言完成图片转 PDF 操作。

---

## Solution

新增「图片转 PDF」工具，支持上传最多 50 张图片（JPG/PNG/WEBP/GIF），可配置页面方向、边距、适配方式，输出为单个 PDF 或 ZIP 包。后端基于 PDFBox 实现，同时注册为 Agent 工具供文档助手调用。

---

## User Stories

1. 作为用户，我希望上传多张图片并将它们合并为一个 PDF 文件，以便统一归档
2. 作为用户，我希望上传 JPG 格式的照片并转为 PDF
3. 作为用户，我希望上传 PNG 格式的截图并转为 PDF
4. 作为用户，我希望上传 WEBP 格式的网络图片并转为 PDF
5. 作为用户，我希望上传 GIF 格式的图片并转为 PDF（取第一帧）
6. 作为用户，我希望一次最多上传 50 张图片
7. 作为用户，我希望上传后能看到每张图片的缩略图预览
8. 作为用户，我希望在预览中删除已上传的图片
9. 作为用户，我希望在已有图片基础上继续添加新图片
10. 作为用户，我希望拖拽调整图片顺序，控制 PDF 中的页面顺序
11. 作为用户，我希望选择 PDF 页面方向为纵向（portrait）
12. 作为用户，我希望选择 PDF 页面方向为横向（landscape）
13. 作为用户，我希望设置页面边距为无（图片撑满整个页面）
14. 作为用户，我希望设置页面边距为小（上下左右各留少量白边）
15. 作为用户，我希望设置页面边距为大（上下左右各留较多白边）
16. 作为用户，我希望选择图片适配方式为 contain（等比缩放，完整显示，可能有留白）
17. 作为用户，我希望选择图片适配方式为 cover（等比缩放，填满页面，可能裁剪）
18. 作为用户，我希望选择图片适配方式为 stretch（拉伸填满，可能变形）
19. 作为用户，我希望将所有图片合并为一个 PDF 下载
20. 作为用户，我选择不合并时，希望每张图片生成独立 PDF 并打包为 ZIP 下载
21. 作为用户，我希望在文件助手中说"把这3张图片转成PDF"来完成操作
22. 作为用户，我希望在文件助手中上传图片后，Agent 自动调用图片转 PDF 工具
23. 作为用户，我希望在文件助手中通过自然语言指定页面方向和边距
24. 作为用户，单张图片超过 5MB 时，我能看到明确的错误提示
25. 作为用户，图片总大小超过 100MB 时，我能看到明确的错误提示
26. 作为用户，上传非图片格式文件时，我能看到明确的错误提示
27. 作为用户，我希望拖拽图片文件到上传区域直接上传
28. 作为用户，我希望点击上传区域弹出文件选择器选择图片
29. 作为用户，我希望在处理过程中看到加载动画
30. 作为用户，处理完成后我希望直接下载 PDF 文件

---

## Implementation Decisions

### 1. 模块划分

新增以下模块：

- **后端 Service 层**：`ImageToPdfService` 接口 + `ImageToPdfServiceImpl` 实现，职责为接收图片字节数组列表 + 配置参数，输出 PDF 字节数组。位于 `service/image/` 包下（不在 `service/pdf/` 下，因为这是图片处理能力，后续图片压缩、格式转换等可归入同一包）
- **后端 Controller 层**：`ImageController`（`/api/image/` 命名空间），作为图片处理的统一入口，本次新增 `image-to-pdf` 端点。位于 `controller/image/` 包下
- **Agent 工具**：`DocAgentToolkit` 新增 `imageToPdf()` @Tool 方法，复用后端 Service
- **前端组件**：`tools/image-to-pdf/index.vue` + `ImageToPdf.vue`，独立工具页面

### 2. API 设计

```
POST /api/image/to-pdf
Content-Type: multipart/form-data
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| files | MultipartFile[] | 必填 | 图片文件，最多 50 个，单个 ≤5MB，总计 ≤100MB |
| orientation | String | portrait | 页面方向：portrait / landscape |
| margin | String | small | 页面边距：none / small / large |
| fitMode | String | contain | 适配方式：contain / cover / stretch |
| merge | boolean | true | 是否合并为一个 PDF |

返回：`ResponseEntity<byte[]>`，`Content-Type: application/pdf` 或 `application/zip`。

### 3. 边距定义

| 级别 | 值（pt） | 说明 |
|------|---------|------|
| none | 0 | 图片撑满整个页面 |
| small | 36 | 约 1.27cm，上下左右各留 |
| large | 72 | 约 2.54cm，上下左右各留 |

### 4. 适配方式实现

| 模式 | 行为 | PDFBox 实现 |
|------|------|------------|
| contain | 等比缩放，完整显示图片，页面可能有留白 | 计算缩放比取 min(scaleX, scaleY)，居中绘制 |
| cover | 等比缩放，填满页面，超出部分裁剪 | 计算缩放比取 max(scaleX, scaleY)，居中绘制，超出页面部分自然裁剪 |
| stretch | 拉伸填满页面，可能变形 | 直接使用页面宽高，不保持比例 |

### 5. PDFBox 实现要点

使用 `PDPage` + `PDImageXObject` + `PDPageContentStream`：
- 创建 `PDDocument`
- 按图片数量循环创建 `PDPage`（设置 MediaBox 为 A4 尺寸）
- 将图片加载为 `PDImageXObject`
- 根据 fitMode 计算目标尺寸和偏移
- 通过 `PDPageContentStream.drawImage()` 绘制
- 输出字节数组

A4 尺寸：portrait = 595×842 pt，landscape = 842×595 pt。

### 6. 文件校验规则

| 校验项 | 规则 | 错误码 |
|--------|------|--------|
| 文件数量 | 1 ≤ n ≤ 50 | IMAGE_FILE_COUNT_INVALID |
| 单文件大小 | ≤ 5MB | IMAGE_FILE_TOO_LARGE |
| 总文件大小 | ≤ 100MB | IMAGE_TOTAL_SIZE_EXCEEDED |
| 文件格式 | .jpg/.jpeg/.png/.webp/.gif | IMAGE_FORMAT_UNSUPPORTED |

### 7. Agent 工具集成

`DocAgentToolkit` 新增 `@Tool` 方法：

```
@Tool(name = "imageToPdf", description = "将多张图片合并为 PDF 文件")
参数:
  - fileIds: String（逗号分隔的文件 ID 列表）
  - orientation: String（portrait/landscape，默认 portrait）
  - margin: String（none/small/large，默认 small）
  - fitMode: String（contain/cover/stretch，默认 contain）
  - merge: boolean（默认 true）
```

Agent 系统提示词需更新，添加 imageToPdf 工具的使用说明。

### 8. 前端组件设计

采用 Variant B 布局（大图网格 + 右侧设置面板），非 file 类工具的三栏布局：

- **左侧**：拖拽上传区 + 图片缩略图网格预览（支持原生 HTML5 拖拽排序、删除、追加上传）
- **右侧**：设置面板（方向/边距/适配/合并参数）+ 转换按钮 + 结果状态

缩略图使用 `URL.createObjectURL()` 本地预览，删除时调用 `URL.revokeObjectURL()` 防止内存泄漏。排序使用原生 HTML5 拖拽（`draggable="true"` + `dragstart/dragover/drop` 事件），不引入 vuedraggable。

### 9. 侧边栏分组

新增 `图片工具包` 分组，图标 🖼️，归属 `file` 分类。后续图片压缩、格式转换等工具可归入此分组。

### 10. 测试 Seams

**主要 seam：`ImageToPdfService`（Service 层）**

这是唯一的业务逻辑 seam。所有图片→PDF 转换逻辑（fitMode 计算、margin 处理、方向旋转、GIF 第一帧提取）都在此处。Controller 只做文件校验和参数委托，Agent 只做参数桥接。测试这个 seam 覆盖了 100% 的业务行为。

**次要 seam：`ImageController`（Controller 层）**

测试文件校验逻辑（数量、大小、格式）和 API 契约（参数绑定、返回 Content-Type、错误码映射）。使用 `@WebMvcTest` + MockBean。

**Agent 层不做独立测试**：`imageToPdf()` @Tool 方法内部只做 loadFile → 委托 Service → store 结果，是纯胶水逻辑，由集成测试覆盖。

---

## Testing Decisions

1. **后端 Service 测试**：单元测试 `ImageToPdfServiceImpl`，使用 PDFBox 创建测试图片字节数组，验证各种 fitMode/margin/orientation 组合的输出。重点验证：
   - contain 模式下图片居中且不超出页面
   - cover 模式下图片填满页面
   - stretch 模式下图片拉伸至页面尺寸
   - 边距值正确应用
   - GIF 取第一帧
   - 输出 PDF 页数与输入图片数一致

2. **Controller 测试**：`@WebMvcTest` 验证文件校验（数量、大小、格式）、API 返回格式、错误码映射

3. **Agent 工具测试**：不独立测试，由 E2E 集成验证覆盖

4. **前端测试**：手动验证上传/预览/排序/删除/参数调整的交互流程

5. **集成测试**：端到端上传图片 → 下载 PDF 的完整流程（E2E 验证清单见 ticket 05）

### Prior art

- `PdfArrangeServiceTest` — 同样使用 PDFBox 创建测试 PDF，验证 Service 层各种组合
- `PdfArrangeControllerTest` — `@WebMvcTest` + MockBean 模式
- `PdfControllerTest` — 文件校验 + API 契约测试

---

## Out of Scope

1. 图片编辑功能（旋转、裁剪、滤镜）— 后续迭代
2. 图片压缩功能 — 独立工具
3. 图片格式转换（如 PNG→JPG）— 独立工具
4. 单张图片大小超过 5MB 的特殊处理（如自动压缩）
5. 非 A4 页面尺寸支持（如 Letter、自定义尺寸）
6. 水印、页码、页眉页脚添加
7. 图片 EXIF 信息保留
8. 使用 vuedraggable 替换原生拖拽排序 — 已决定保持原生 HTML5 拖拽

---

## Further Notes

- `ImageController` 设计为图片处理的统一入口，后续新增图片相关功能（压缩、格式转换、OCR 等）统一挂载到 `/api/image/` 命名空间
- Agent 工具复用后端 Service，不重复实现逻辑
- 前端组件使用原生 HTML5 拖拽排序（与 pdf-arrange 的 vuedraggable 方案不同），保持轻量
- `ImageToPdfService` 放在 `service/image/` 包下（而非 `service/pdf/`），语义更清晰
- GIF 第一帧提取由 `ImageIO.read()` 天然支持，无需额外处理
