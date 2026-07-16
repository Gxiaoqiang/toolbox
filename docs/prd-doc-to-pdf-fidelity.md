# PRD: 文档转 PDF 格式保真优化

**日期**: 2026-07-14  
**状态**: ready-for-agent  
**标签**: `ready-for-agent`

---

## Problem Statement

用户在使用文档转 PDF 功能时，转换后的 PDF 文件中出现**原始 Word 文档不存在的多余换行**。这导致 PDF 排版与原始 Word 文档不一致，影响文档的可信度和可用性。用户期望 PDF 的样式、格式、换行与原始 Word 文档**完全一致**。

---

## Solution

通过三个层面提升转换保真度：

1. **LibreOffice 命令行优化** — 显式指定 PDF 导出过滤器、输入格式过滤器
2. **持久化 LibreOffice 用户配置** — 预置优化过的 PDF 导出配置，避免每次重建 profile
3. **字体嵌入** — 强制嵌入字体，防止字体替换导致的布局偏移

---

## User Stories

1. 作为文档处理用户，我希望 docx 转 PDF 后的换行位置与原始文件完全一致，不会出现多余的空行或断行
2. 作为文档处理用户，我希望 doc 格式（旧版 Word）转 PDF 的排版与 Word 中看到的一致
3. 作为文档处理用户，我希望包含中文、英文混排的文档转换后不出现意外的折行
4. 作为文档处理用户，我希望文档中的表格在 PDF 中保持列宽和单元格换行不变
5. 作为文档处理用户，我希望文档中的页眉页脚、页码在 PDF 中保持原样
6. 作为运维人员，我希望优化后的转换服务仍保持可接受的性能（不显著增加转换时间）
7. 作为开发者，我希望 PDF 导出配置可集中管理，方便后续调优

---

## Implementation Decisions

### 1. 显式指定 PDF 导出过滤器

当前命令: `--convert-to pdf`  
优化后: `--convert-to pdf:writer_pdf_Export`

LibreOffice 的 `--convert-to` 支持 `<format>:<filter_name>` 语法。`writer_pdf_Export` 是 Writer 专用的高质量 PDF 导出过滤器，比默认自动选择更可靠。

### 2. 显式指定输入格式过滤器

添加 `--infilter` 参数，根据文件扩展名指定输入过滤器：

| 扩展名 | infilter 值 |
|--------|-----------|
| .docx | `MS Word 2007 XML` |
| .doc | `MS Word 97` |
| .wps | 自动检测（无专用过滤器） |

这避免了 LibreOffice 自动检测格式时可能的误判。

### 3. 持久化 LibreOffice 用户配置

当前：每个转换请求创建新的临时 profile（`HOME=/tmp`），每次重建字体缓存。  
优化后：使用 `-env:UserInstallation=file:///tmp/lo-profile` 指向固定目录，首次启动时自动创建优化的 profile，后续复用。

在 Docker 构建时（`base.Dockerfile`）预初始化 profile，写入优化的 PDF 导出配置到 `registrymodifications.xcu`，确保以下参数生效：

- `EmbedFonts` = true（嵌入字体，防止替换）
- `EmbedStandardFonts` = true（嵌入标准字体）
- `IsSkipEmptyPages` = false（不跳过空页，保持页面顺序）
- `UseLosslessCompression` = true（图片无损压缩）
- `ReduceImageResolution` = false（不降低图片分辨率）
- `SelectPdfVersion` = 1（PDF/A-1b，确保跨平台一致性）
- `ExportBookmarks` = true（导出书签/目录结构）
- `ExportNotesPages` = false（不导出批注页）

### 4. 字体嵌入策略

预置 profile 中配置 `EmbedFonts=true` 和 `EmbedStandardFonts=true`。Docker 镜像中已安装 5 款中文字体 + Liberation 西文字体，嵌入字体可消除字体替换导致的布局漂移。

### 5. 新增 Docker 构建步骤

`base.Dockerfile` 在安装 LibreOffice 后，增加一步：
1. 首次启动 soffice（`--headless --norestore`），触发默认 profile 创建
2. 向 `registrymodifications.xcu` 写入 PDF 导出优化参数
3. 关闭 soffice，固化配置

---

## Testing Decisions

### 测试范围

- **单元测试**：验证 `DocumentServiceImpl` 命令参数包含 `pdf:writer_pdf_Export` 和 `--infilter`
- **集成测试**：用预设的中文 .docx 测试文件转换，人工验证 PDF 换行与原文一致
- **回归测试**：确保现有 42 个测试全部通过

### 测试文件

- 准备一个已知会触发多余换行的中文 .docx 文件作为回归用例
- 验证转换后的 PDF 文件字节数、页数与预期一致

---

## Out of Scope

- Word 中复杂排版元素（文本框、艺术字、SmartArt）的 100% 保真 — LibreOffice 对此类元素支持有限，不在本次范围
- .ppt / .pptx 转 PDF — 当前仅支持 .doc/.docx/.wps
- 微软专有字体（SimSun、SimHei 等）的嵌入 — 涉及字体版权，使用开源等价字体替代
- PDF 页面级别的像素级对比 — 以肉眼可见的换行/段落一致性为准

---

## Further Notes

- LibreOffice 7.3.7（Ubuntu 22.04 官方包）的 PDF 导出过滤器参数通过 `registrymodifications.xcu` 持久化，这是 LibreOffice 官方推荐的无 GUI 配置方式
- 如果后续需要更精细的控制（如自定义页面边距、水印），可以通过 UNO API（Python/LibreOfficeKit）实现，但不在本次范围
- `.wps` 格式由 WPS Office 原生，LibreOffice Writer 对其支持有限，WPS 文件转换保真度可能始终低于 .docx
