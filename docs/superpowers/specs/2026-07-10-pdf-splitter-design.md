# PDF 切分工具 — 设计文档

> @author toolbox-dev
> @since 2026-07-10
> 状态: 已确认

## 1. 概述

新增 PDF 切分工具，支持三种切分模式：逐页拆分、按页码范围、每 N 页拆分。前端提供上传 + 配置界面，后端使用 Apache PDFBox 处理并返回 ZIP 包。

## 2. 技术选型

**Apache PDFBox 3.0.3** — Apache 2.0 许可，与 MIT 项目兼容。

```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>
```

## 3. API 设计

```
POST /api/pdf/split
  Content-Type: multipart/form-data
  Params:
    file:          MultipartFile  (必填)
    mode:          String         (必填: "by-page" | "by-range" | "by-n")
    pages:         String         (mode=by-range 时必填)
    everyN:        Integer        (mode=by-n 时必填)
    preserveMeta:  Boolean        (可选，默认 false)
  Response:
    200: application/zip (Content-Disposition: attachment; filename="pdf-split-result.zip")
```

## 4. 切分模式

### 4.1 逐页拆分 (by-page)

- 参数：无
- 结果：每页一个独立 PDF
- 命名：`{原文件名}_p{页码}.pdf`

### 4.2 按页码范围 (by-range)

- 参数：`pages`，逗号分隔的页码/区间表达式
- 语法：`页码` 或 `起始-结束`，逗号拼接，如 `1,3,5-8,10`
- 结果：每个区间一个 PDF
- 命名：单页 `{原文件名}_p{页码}.pdf`，多页 `{原文件名}_p{起始}-{结束}.pdf`

### 4.3 每 N 页拆分 (by-n)

- 参数：`everyN`，正整数
- 结果：每 N 页一个 PDF，最后一组不足 N 页保持原样
- 命名：`{原文件名}_part{序号}.pdf`

## 5. 页码范围校验

### 5.1 格式校验

- 只能包含数字、逗号、短横线
- 区间 `起始-结束` 必须 `起始 ≤ 结束`
- 不允许空值、连续逗号、首尾逗号

### 5.2 重复/重叠校验

将输入展开为页码集合后：

- 检测完全重复的页码（如 `1, 3, 3`）
- 检测跨区间重叠（如 `1-5, 3-8` 重叠在第 3,4,5 页）
- 检测单页与区间重叠（如 `1-5, 2` 重叠在第 2 页）

### 5.3 越界校验

- 所有页码 ≥ 1
- 所有页码 ≤ PDF 总页数

### 5.4 校验分层

| 层 | 时机 | 内容 |
|----|------|------|
| 前端 | 输入时实时 | 格式 + 重复/重叠（已知总页数时加越界） |
| 后端 | 请求处理 | 所有规则兜底校验 |

## 6. 文件命名

| 模式 | 格式 | 示例 |
|------|------|------|
| 逐页拆分 | `{原文件名}_p{页码}.pdf` | `报告_p1.pdf` |
| 按页码范围（单页） | `{原文件名}_p{页码}.pdf` | `报告_p3.pdf` |
| 按页码范围（多页） | `{原文件名}_p{起始}-{结束}.pdf` | `报告_p5-8.pdf` |
| 每 N 页拆分 | `{原文件名}_part{序号}.pdf` | `报告_part1.pdf` |

## 7. 元数据处理

- 前端提供复选框：`☐ 保留原始 PDF 元数据`
- 默认不保留（轻量处理）
- 保留模式下，通过 `PDDocument.getDocumentInformation()` 读取并写入各拆分文件

## 8. 核心实现约束

- 逐页复制（`PDDocument.addPage()` + `PDPage` copy），确保原始页面内容不变
- 每个输出 PDF 独立创建 `PDDocument` 实例，无页面交叉引用导致的重复
- `ZipOutputStream` 逐文件写入，不产生临时文件
- 内存敏感：大文件（接近 50MB）逐页处理，避免全量加载所有拆分结果

## 9. 错误处理

| 错误码 | 场景 | HTTP | 提示信息 |
|--------|------|------|---------|
| `PDF_4001` | 非 PDF 文件 | 400 | 请上传 PDF 格式的文件 |
| `PDF_4002` | 文件为空 | 400 | 请选择有效的 PDF 文件 |
| `PDF_4003` | PDF 已加密 | 400 | 暂不支持加密的 PDF 文件 |
| `PDF_4004` | 页码超出范围 | 400 | 页码范围超出文档总页数（共 X 页） |
| `PDF_4005` | 页码格式错误 | 400 | 页码范围格式不正确，请输入如 "1,3,5-8" |
| `PDF_4006` | N 值无效 | 400 | 每页拆分数量必须为正整数 |
| `PDF_4007` | 页码重复/重叠 | 400 | 页码范围存在重复或重叠 |
| `PDF_5001` | 处理异常 | 500 | PDF 处理失败，请稍后重试 |

## 10. 前端 UI 布局

沿用现有工具组件的左右分栏布局：

**左侧 — 上传区**:
- 拖拽上传 + 点击选择文件
- 显示：文件名、总页数、文件大小
- 支持重新选择

**右侧 — 配置区**:
- 模式选择：Radio Group（逐页拆分 / 按页码范围 / 每 N 页拆分）
- 动态参数：
  - `by-page`: 预览提示 "将拆分为 X 个独立 PDF"
  - `by-range`: 文本输入框 + 实时校验反馈
  - `by-n`: 数字输入框
- 复选框：`☐ 保留原始 PDF 元数据`
- 按钮：执行拆分（loading 状态）/ 下载 ZIP

## 11. 前后端文件结构

```
frontend/src/tools/pdf-splitter/
  └── index.vue                        # 完整工具组件

backend/src/main/java/com/toolbox/
  ├── controller/pdf/PdfController.java
  ├── service/pdf/PdfService.java
  ├── service/pdf/impl/PdfServiceImpl.java
  └── model/dto/PdfSplitRequest.java
```

## 12. 约束 & 非目标

- 不支持加密 PDF（MVP 阶段提示用户）
- 单文件上限 50MB（与现有 multipart 配置一致）
- 不支持多文件批量上传（单次处理一个 PDF）
- 不提供在线预览缩略图
