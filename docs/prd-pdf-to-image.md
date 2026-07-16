# PRD: PDF 转图片

**日期**: 2026-07-14  
**状态**: implemented  
**标签**: `ready-for-agent`

---

## Problem Statement

用户需要将 PDF 文件的每一页转换为图片格式（PNG/JPEG），以便在网页预览、社交媒体分享或文档缩略图场景中使用。需要支持自定义分辨率（DPI）、图片格式选择、JPEG 质量调节以及页码范围指定。

---

## Solution

基于已有 PDFBox 3.0.3 的 `PDFRenderer`，将 PDF 逐页渲染为 `BufferedImage`，通过 JDK 内置 `ImageIO` 编码输出。零额外依赖。

---

## User Stories

1. 作为用户，我希望上传 PDF 后将其每一页转为独立图片
2. 作为用户，我希望自由输入 DPI 值（72-600），控制清晰度与文件大小
3. 作为用户，我希望选择 PNG（无损）或 JPEG（有损）输出格式
4. 作为用户，选 JPEG 时我希望调节压缩质量（10%-100%）
5. 作为用户，我希望指定页码范围（如 1-5, 7, 9-12），而非每次都转换全部
6. 作为用户，转换多页时以 ZIP 包下载，单页时直接下载图片
7. 作为用户，输入 DPI 后我能看到预估的图像像素尺寸
8. 作为用户，文件超过 50MB 或格式不对时我能看到明确的错误提示

---

## Implementation Decisions

### 1. 技术选型

使用 PDFBox 3.0.3 `PDFRenderer`，项目已依赖此包，无需新增 Maven 依赖。PNG/JPEG 编码由 JDK `ImageIO` 内置支持。

### 2. API 设计

```
POST /api/pdf/to-image
Content-Type: multipart/form-data
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| file | MultipartFile | 必填 | PDF ≤ 50MB |
| dpi | int | 200 | 范围 72-600 |
| format | String | "png" | png / jpeg |
| quality | float | 0.9 | JPEG 质量 0.0-1.0 |
| pageRange | String | 空=全部 | 如 "1-5" 或 "1,3,5" |

返回：单页 → `image/png` 或 `image/jpeg`；多页 → `application/zip`

### 3. 分层架构

| 层 | 职责 |
|----|------|
| Controller | 文件非空校验、扩展名校验、大小校验，委托 Service |
| Service | 参数校验、PDF 加载、页面渲染、ZIP 打包、文件名生成 |
| DTO | `PdfToImageResult`（data + contentType + filename） |
| 常量 | `ImageConvertConstant` — DPI 范围、格式枚举 `ImageFormat`、文件大小上限 |

### 4. 常量化

所有魔法值提取到 `ImageConvertConstant`：

```java
MIN_DPI = 72, MAX_DPI = 600, DEFAULT_DPI = 200
DEFAULT_JPEG_QUALITY = 0.9f
MAX_FILE_SIZE = 50MB

enum ImageFormat { PNG, JPEG }  // 自带 extension / mimeType / from()
```

### 5. 页面范围解析

支持格式：空（全部）、`"1-5"`、`"1,3,5"`、`"1-3,5,7-9"`。0-based 内部索引，异常时抛出 `PDF_PAGE_OUT_OF_RANGE` 或 `PDF_PAGE_FORMAT_ERROR`。

### 6. 并行渲染决策

**不使用线程池并行渲染**。原因：
- PDFBox `PDDocument` 非线程安全，多线程并发读取页树会抛 `IllegalStateException: 1-based index not found`
- 每个线程独立 `LoadPDF` 拷贝副本会导致内存膨胀（50MB × N 线程）
- 单次 `LoadPDF` + 串行逐页渲染，内存可控，逻辑简单

项目中保留 `ThreadPoolConfig` 全局线程池供其他场景使用。

### 7. 全局线程池

`toolboxExecutor` Bean：`corePoolSize = CPU核数`，`maxPoolSize = CPU×2`，`queueCapacity = 3000`，`CallerRunsPolicy`。供未来异步任务场景复用。

---

## Testing Decisions

- 测试 Service 的 `parsePageRange` 方法（纯逻辑，无需 PDF 文件）
- 测试 Controller 的文件校验逻辑（空文件、非 PDF 扩展名、超大文件）
- 42 个已有回归测试全部通过
- 集成测试：用真实 PDF 文件调用 API，人工验证输出图片质量

---

## Files

| 文件 | 操作 |
|------|------|
| `backend/.../service/pdf/PdfToImageService.java` | 新建 — 接口 |
| `backend/.../service/pdf/impl/PdfToImageServiceImpl.java` | 新建 — 实现 |
| `backend/.../controller/pdf/PdfToImageController.java` | 新建 — Controller |
| `backend/.../service/pdf/ImageConvertConstant.java` | 新建 — 常量 + ImageFormat 枚举 |
| `backend/.../service/pdf/PdfToImageResult.java` | 新建 — 结果 DTO |
| `backend/.../config/ThreadPoolConfig.java` | 新建 — 全局线程池 |
| `backend/.../exception/ErrorCodeEnum.java` | 修改 — 新增 4 个 PDF_IMAGE_* 错误码 |
| `frontend/src/tools/pdf-to-image/index.vue` | 新建 — 前端页面 |

---

## Out of Scope

- OCR 文字识别、图片水印叠加、TIFF/WebP 输出
- 超大 PDF 转换进度推送（WebSocket）
- 页面裁剪/旋转（已有 pdf-splitter 可配合）
