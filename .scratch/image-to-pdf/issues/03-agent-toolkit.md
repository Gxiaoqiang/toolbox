# 03 — Agent Toolkit 集成 (imageToPdf @Tool)

**What to build:** 在 `DocAgentToolkit` 中新增 `imageToPdf()` @Tool 方法，让文档助手 Agent 能通过自然语言调用图片转 PDF 功能。同时更新 Agent 系统提示词。

**Blocked by:** 02 — 需要 `ImageToPdfService` 已实现并可注入

**Status:** ready-for-agent

- [ ] `DocAgentToolkit` 构造函数注入 `ImageToPdfService`
- [ ] `DocAgentConfig` 的 `docAgentToolkit()` Bean 方法签名同步更新
- [ ] 新增 `@Tool(name = "imageToPdf")` 方法，参数：`fileIds`（逗号分隔）、`orientation`、`margin`、`fitMode`、`merge`
- [ ] 内部流程：loadFile → 收集 bytes + extensions → 调用 ImageToPdfService → store 结果 → 返回摘要
- [ ] `merge=false` 时逐张转 PDF 后打包 ZIP（Controller 层逻辑复用到 Agent 层）
- [ ] 更新 `doc-agent-system.md` 系统提示词：工具参数速查表新增图片转 PDF 行、规则新增 imageToPdf 使用说明
- [ ] 后端 `mvn compile` 通过
