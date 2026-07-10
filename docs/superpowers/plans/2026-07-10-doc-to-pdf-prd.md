# 文档转 PDF — PRD

> 日期: 2026-07-10 | 状态: ready-for-agent | 关联 spec: `docs/superpowers/specs/2026-07-10-doc-to-pdf-design.md`

## Problem Statement

用户持有 .doc / .docx / .wps 格式的文档，需要转换为 PDF 以便分发、打印或归档。目前工具箱只能切分已有 PDF，无法将 Office 文档转为 PDF。

## Solution

新增文档转 PDF 工具，利用项目依赖的 LibreOffice headless 实现转换。支持批量上传（最多 5 个），结果打包 ZIP 统一下载。单文件失败不影响整体，错误详情记录在 ZIP 内的 `_errors.json` 中。

## User Stories

1. 作为用户，我想上传 .docx 文件并转换为 PDF，以便在任意设备上查看
2. 作为用户，我想上传 .doc 文件并转换为 PDF，保留格式和布局
3. 作为用户，我想上传 .wps 文件并转换为 PDF，以统一文档格式
4. 作为用户，我想一次上传多个文档（最多 5 个），批量转换后下载 ZIP，而不需要逐个操作
5. 作为用户，我想看到每个文件的转换状态（成功或失败），以便了解哪些文件需要重新处理
6. 作为用户，我想在批量转换中部分文件失败时仍能下载成功的文件
7. 作为用户，我想通过拖拽方式上传文件，操作更便捷
8. 作为用户，我想在上传非支持格式或超大文件时得到明确提示

## Implementation Decisions

### 转换引擎

LibreOffice headless，通过 Spring Boot `ProcessBuilder` 调用：

```bash
soffice --headless --norestore --convert-to pdf --outdir <tmpdir> <input>
```

- Mac 开发：`brew install libreoffice` 后可用
- Linux 离线部署：预装 `libreoffice-headless` RPM
- 单文件超时 60 秒，`ProcessBuilder#waitFor(timeout)`
- 启动时健康检查：`soffice --version`

### 批量处理策略

- 串行处理（单用户场景）
- 每个文件独立临时目录，处理完立即清理
- 失败文件记录到 `_errors.json`，不影响后续文件

### 文件命名

`{原名}_{原扩展名}_converted.pdf`

### 参数校验

| 校验项 | 前端 | 后端 |
|--------|------|------|
| 扩展名白名单 (doc/docx/wps) | ✅ | ✅ |
| 单文件 ≤50MB | ✅ | ✅ |
| 数量 ≤5 | ✅ | ✅ |
| 文件非空 | — | ✅ |
| LibreOffice 可用 | — | ✅ (启动健康检查) |

### 前端页面设计

沿用左右分栏：左侧上传区 + 右侧配置区

**上传阶段**：
- 虚线拖拽区（点击/拖拽上传）
- 已选文件列表，每项右侧有 ✕ 移除按钮
- 上传提示：支持格式、大小限制、数量限制

**转换阶段**：
- 点击"开始转换"后，每个文件名前出现 ⏳（处理中）→ ✅（成功）/ ❌（失败）
- 全部完成后显示"下载 ZIP"按钮

**校验反馈**：
- 扩展名不对：文件列表中标记 ⚠ 并提示
- 超过 5 个：拒绝添加并提示
- 超过 50MB：拒绝并提示

## Testing Decisions

### Seams

主接缝：API 层 `POST /api/convert/doc-to-pdf` — MockMvc multipart 测试

### 测试策略

- 后端 API 集成测试：上传合法文件 → 200 + ZIP 流
- 后端参数校验：错误扩展名 → 400、空文件 → 400、超量文件 → 400
- LibreOffice 可用性测试：启动时检查 `soffice` 命令
- 转换质量验证：人工验证输出 PDF 与原文档对比

### 开发环境测试注意

- Mac 需要 `brew install libreoffice`
- CI 环境需安装 libreoffice 依赖
- 测试中可使用 `@DisabledOnOs(OS.LINUX)` 在无 LO 环境跳过

## Out of Scope

- .pages、.odt、.rtf 等其他格式（仅 doc/docx/wps）
- 在线预览 PDF（直接下载）
- 自定义输出文件名模板（使用固定命名规则）
- 并行批量转换（串行处理）
- 转换后进一步操作（水印、加密、合并）
