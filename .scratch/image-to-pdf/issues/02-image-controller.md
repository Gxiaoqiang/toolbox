# 02 — ImageController REST API

**What to build:** 新增 `ImageController`（`/api/image/` 命名空间），实现 `POST /api/image/to-pdf` 端点。接收 multipart 图片文件 + 配置参数，校验后委托 `ImageToPdfService` 转换，返回 PDF 或 ZIP 文件下载。

**Blocked by:** 01 — 需要 `ImageToPdfService` 和 `IMAGE_*` 错误码

**Status:** ready-for-agent

- [ ] 创建 `ImageController`（`backend/src/main/java/com/toolbox/controller/image/ImageController.java`），`@RequestMapping("/api/image")`
- [ ] `POST /to-pdf` 端点，参数：`files`（MultipartFile[]）、`orientation`（默认 portrait）、`margin`（默认 small）、`fitMode`（默认 contain）、`merge`（默认 true）
- [ ] 文件校验：1 ≤ n ≤ 50、单文件 ≤5MB、总大小 ≤100MB、格式仅 jpg/jpeg/png/webp/gif（通过 MIME type + 扩展名双重校验）
- [ ] `merge=true` 时返回 `application/pdf`，`merge=false` 时每张图独立生成 PDF 后打包为 `application/zip` 返回
- [ ] Content-Disposition 文件名：`images.pdf` 或 `images.zip`，URL 编码
- [ ] 编写 `ImageControllerTest`（`@WebMvcTest`），验证：正常转换返回 PDF、文件为空返回 400、超过数量限制返回 400、格式不支持返回 400、merge=false 返回 ZIP
- [ ] 后端 `mvn test` 全部 PASS
