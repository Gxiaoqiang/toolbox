# 文档转 PDF — 设计文档

> @author toolbox-dev
> @since 2026-07-10
> 状态: 已确认

## 1. 概述

新增文档转 PDF 工具，支持上传 .doc / .docx / .wps 文档，使用 LibreOffice headless 转换为 PDF。支持批量上传（最多 5 个文件），结果打包为 ZIP 下载。

## 2. 技术选型

**LibreOffice headless**，通过 CLI 调用 `soffice --headless --convert-to pdf`。

- Mac 开发环境：安装 `libreoffice` (brew install)
- Linux 离线部署：预装 libreoffice-headless RPM 包
- Spring Boot 通过 `ProcessBuilder` 调用，设置超时保护

```xml
<!-- pom.xml 无需额外依赖，纯 ProcessBuilder 调用 -->
```

## 3. API 设计

```
POST /api/document/convert-to-pdf
  Content-Type: multipart/form-data
  Params:
    files:  MultipartFile[]  (必填，最多 5 个，每个 ≤50MB)
  Response:
    200: application/zip
         Content-Disposition: attachment; filename="doc-to-pdf-result.zip"
    400: { code, message, data }
    500: { code, message, data }
```

**失败处理**: 单个文件转换失败不影响其他文件。成功的 PDF 打包进 ZIP，失败的在 ZIP 根目录放一个 `_errors.json` 列出失败详情。

## 4. 转换流程

```
用户上传 N 个文件
  → 校验：扩展名合法 / 大小 / 数量 ≤5
  → 逐一调用 soffice --headless --convert-to pdf
  → 成功 → PDF 加入 ZIP 列表
  → 失败（含超时 60s）→ 记录错误信息
  → 全部完成后，生成 ZIP:
      ├── 报告_docx_converted.pdf
      ├── 合同_doc_converted.pdf
      ├── 会议纪要_wps_converted.pdf
      └── _errors.json  (如有失败)
```

## 5. 文件命名规则

`{原名}_{原扩展名}_converted.pdf`

| 输入 | 输出 |
|------|------|
| `报告.docx` | `报告_docx_converted.pdf` |
| `合同.doc` | `合同_doc_converted.pdf` |
| `会议纪要.wps` | `会议纪要_wps_converted.pdf` |

## 6. 文件校验

| 层 | 时机 | 内容 |
|----|------|------|
| 前端 | 选择文件时 | 扩展名校验、数量 ≤5、单文件 ≤50MB |
| 后端 | 请求处理 | 扩展名白名单、文件非空、数量 ≤5、soffice 可用性检查 |

### 支持的扩展名

```java
private static final Set<String> ALLOWED_EXTENSIONS = Set.of("doc", "docx", "wps");
```

## 7. 错误处理

| 错误码 | 场景 | HTTP | 提示信息 |
|--------|------|------|---------|
| `DOC_4001` | 扩展名不支持 | 400 | 仅支持 .doc / .docx / .wps 格式 |
| `DOC_4002` | 文件为空 | 400 | 请选择有效的文档文件 |
| `DOC_4003` | 超过 5 个文件 | 400 | 单次最多上传 5 个文件 |
| `DOC_4004` | 文件超过 50MB | 400 | 单个文件不能超过 50MB |
| `DOC_5001` | 转换失败 | 500 | 文档转换失败（不影响其他文件） |
| `DOC_5002` | LibreOffice 不可用 | 500 | 转换服务不可用，请联系管理员 |

### _errors.json 格式

```json
{
  "failed": [
    { "filename": "bad.doc", "reason": "转换超时（60秒）" },
    { "filename": "corrupt.docx", "reason": "文件已损坏，无法打开" }
  ]
}
```

## 8. LibreOffice 调用

```bash
soffice --headless --norestore --convert-to pdf --outdir /tmp/output input.docx
```

- 超时：单文件 60 秒
- 输出目录：每次创建临时目录，处理完清理
- 并发：串行处理（ProcessBuilder#waitFor）
- 启动时健康检查：检查 `soffice --version` 可用

## 9. 前端 UI 布局

沿用左右分栏：

```
┌──────────────────────────────────────────────────────┐
│  📝 文档转 PDF                                        │
├────────────────────────┬─────────────────────────────┤
│                        │                             │
│   ┌──────────────┐     │  支持格式：.doc .docx .wps   │
│   │  📤 拖拽上传   │     │  单次最多上传 5 个文件       │
│   │  或点击选择    │     │  单文件不超过 50MB          │
│   └──────────────┘     │                             │
│                        │  ┌───────────────────────┐   │
│   已选文件列表:          │  │ 文件列表（转换后）：    │   │
│   ┌─────────────┐      │  │ ✅ 报告_docx_converted │   │
│   │ 报告.docx   ✕  │    │  │ ✅ 合同_doc_converted  │   │
│   │ 合同.doc    ✕  │    │  │ ❌ 简历.wps (失败)     │   │
│   │ 简历.wps    ✕  │    │  └───────────────────────┘   │
│   └─────────────┘      │                             │
│                        │  [ 📥 下载 ZIP ]            │
│   [ 开始转换 ]          │                             │
├────────────────────────┴─────────────────────────────┤
│  选择一个工具开始使用                                    │
└──────────────────────────────────────────────────────┘
```

## 10. 前后端文件结构

```
backend/src/main/java/com/toolbox/
  ├── controller/document/DocumentController.java     # 新增 POST /api/document/convert-to-pdf
  ├── service/document/DocumentService.java           # 接口
  ├── service/document/impl/DocumentServiceImpl.java  # LibreOffice 调用实现
  └── exception/ErrorCodeEnum.java                    # 新增 DOC_4001~DOC_5002

frontend/src/tools/doc-to-pdf/
  └── index.vue
```

> 注意：文档转换使用独立的 `controller/document/` 和 `service/document/` 包，与 PDF 切分的 `controller/pdf/` 和 `service/pdf/` 并列，职责隔离。

## 11. 约束 & 非目标

- 不修改原文件内容，直接转换（不加水印、不加密）
- 暂不支持 .pages、.odt 等其他文档格式
- 不提供在线预览（直接下载）
- 单文件 50MB，最多 5 个文件
- 开发环境 Mac + 部署环境离线 Linux
