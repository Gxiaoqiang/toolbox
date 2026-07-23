## 图片转 PDF 工具

### imageToPdf — 图片合并为 PDF
- 支持格式：JPG / PNG / WEBP / GIF
- 最多 50 张图片，单张 ≤5MB
- 可选参数：

| 参数 | 默认值 | 可选值 |
|------|--------|--------|
| orientation | portrait | portrait(纵向) / landscape(横向) |
| margin | small | none(无边距) / small(小) / large(大) |
| fitMode | contain | contain(等比完整) / cover(裁剪填满) / stretch(拉伸变形) |
| merge | true | true(合并为单个PDF) / false(每张独立PDF打包ZIP) |

- GIF 格式自动取第一帧，无需额外处理。
