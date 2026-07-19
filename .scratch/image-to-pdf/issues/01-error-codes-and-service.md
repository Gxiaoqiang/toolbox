# 01 — 后端错误码 + ImageToPdfService (TDD)

**What to build:** 在 `ErrorCodeEnum` 中新增 `IMAGE_*` 系列错误码，并实现 `ImageToPdfService` 接口 + `ImageToPdfServiceImpl`，使用 PDFBox 将多张图片按配置（方向、边距、适配方式）转换为 PDF 字节数组。支持合并输出单 PDF 或每张独立 PDF（供后续 Controller 决定是否打 ZIP）。

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

- [ ] `ErrorCodeEnum` 新增 4 个错误码：`IMAGE_FILE_COUNT_INVALID`、`IMAGE_FILE_TOO_LARGE`、`IMAGE_TOTAL_SIZE_EXCEEDED`、`IMAGE_FORMAT_UNSUPPORTED`
- [ ] 创建 `ImageToPdfService` 接口（`backend/src/main/java/com/toolbox/service/image/ImageToPdfService.java`），方法签名：`byte[] convert(List<byte[]> images, List<String> extensions, String orientation, String margin, String fitMode)`，返回合并后的单个 PDF
- [ ] 创建 `ImageToPdfServiceImpl`（`backend/src/main/java/com/toolbox/service/image/impl/ImageToPdfServiceImpl.java`），基于 PDFBox `PDPage` + `PDImageXObject` + `PDPageContentStream.drawImage()` 实现
- [ ] 支持三种适配方式：contain（等比居中）、cover（等比裁剪）、stretch（拉伸变形）
- [ ] 支持三种边距：none(0pt)、small(36pt)、large(72pt)
- [ ] 支持两种方向：portrait(595×842)、landscape(842×595)
- [ ] GIF 格式取第一帧（`ImageIO.read()` 天然支持）
- [ ] 编写 `ImageToPdfServiceTest` 单元测试，覆盖：单图转换、多图合并、各种 fitMode 组合、各种 margin 组合、portrait/landscape、GIF 第一帧
- [ ] 后端 `mvn test` 全部 PASS
- [ ] 编译验证 `mvn compile`
