# 文档处理 Agent 统一入口 — 设计文档

> 日期: 2026-07-15
> 状态: 设计确认

---

## 1. 概述

### 1.1 背景

当前 Toolbox 的文档工具（PDF 切分/合并/压缩/转图片 + 文档转 PDF + Markdown 转 DOCX）各为独立页面，用户需要：
- 知道每个工具的存在
- 了解每个工具的用法和参数
- 逐个点击进入操作

### 1.2 目标

构建一个 **AI 对话式文档处理 Agent**，用户通过自然语言描述需求，Agent 自动识别意图、收集参数、调用后端工具、返回结果。独立工具页面保留作为快捷入口。

### 1.3 技术选型

- **Agent 框架**: AgentScope Java 1.0.10（阿里开源，Spring Boot 原生集成）
- **LLM**: 阿里云百炼 DashScope（qwen-plus），预留 deepseek/openai 切换
- **前端**: Vue 3 + TypeScript，对话 UI 组件
- **协议**: SSE（Server-Sent Events）流式响应

---

## 2. 架构设计

```
前端 (doc-agent/index.vue)             后端 Agent 层 (新增)
┌──────────────────────┐    POST       ┌─────────────────────────────┐
│  对话 UI              │  /api/agent   │ AgentController             │
│  · 消息气泡            │  /chat        │   → AgentService            │
│  · 文件附件上传         │  (SSE)        │     → ReActAgent            │
│  · 结果卡片下载         │               │       → DocAgentToolkit     │
│  · 快捷选项按钮         │               │         → PdfService(复用)  │
└──────────────────────┘               │         → DocumentService    │
                                        │         → MarkdownService    │
                                        │     → ConversationManager   │
                                        └─────────────────────────────┘
```

**核心原则**: Agent 层只做编排和意图路由，实际文档处理委托给现有 Service，不修改现有代码。

---

## 3. 前端设计

### 3.1 组件: `frontend/src/tools/doc-agent/index.vue`

- **meta**: `{ id: 'doc-agent', name: '文档助手', icon: '🤖', category: 'file', group: 'PDF 工具包' }`
- **布局**: 全屏对话界面（顶部标题栏 + 中间消息列表 + 底部输入区）

### 3.2 对话状态机

```
IDLE → (用户输入) → TEXT_ONLY / FILE_ONLY → READY → PROCESSING → DONE/ERROR
                                  │                        │
                                  └── 缺参数则追问 ←────────┘
```

| 状态 | 说明 | 用户操作 |
|------|------|---------|
| IDLE | 进入页面，AI 主动打招呼 | 输入文字或上传文件 |
| TEXT_ONLY | 用户只发了文字 | AI 解析意图，缺文件追问 |
| FILE_ONLY | 用户只传了文件 | AI 追问操作意图 |
| READY | 意图+文件+参数齐全 | AI 确认参数，开始处理 |
| PROCESSING | 处理中，输入框锁定 | 不可打断 |
| DONE | 处理完成 | 下载结果，继续对话 |
| ERROR | 处理异常 | 查看原因，按建议重试 |

### 3.3 消息类型

| 类型 | 渲染组件 | 示例 |
|------|---------|------|
| 文字消息 | 聊天气泡 | 用户/AI 文本 |
| 文件附件 | 文件卡片（图标+名称+大小） | `📎 report.pdf (15MB)` |
| 快捷选项 | 气泡内按钮组 | "切分" / "合并" / "压缩" |
| 处理结果 | 结果卡片（下载按钮） | `📦 result.zip (14.8MB) ⬇` |
| 错误提示 | 错误卡片（红色边框+建议） | 错误原因 + 重试引导 |

### 3.4 文件上传交互

- **进入页面**: AI 主动打招呼，列出能力范围
- **用户先传文件**: AI 追问操作意图，提供快捷选项按钮
- **用户先说需求**: AI 追问文件，提示支持格式
- **用户同时传文件+需求**: AI 确认参数后直接处理
- **对话中追加文件**: 支持随时上传，替换或补充已有文件

---

## 4. 后端设计

### 4.1 AgentScope 集成

**依赖**:
```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-spring-boot-starter</artifactId>
    <version>1.0.10</version>
</dependency>
```

**配置** (`application.yml`):
```yaml
agentscope:
  core:
    agent:
      max-steps: 8
      name: doc-assistant
  model:
    provider: dashscope
    model-name: qwen-plus
    api-key: ${DASHSCOPE_API_KEY}
```

### 4.2 工具定义 (DocAgentToolkit)

6 个 `@Tool` 注解方法，每个封装一个现有 Service：

| @Tool name | 封装 Service | 关键参数 |
|-----------|-------------|---------|
| `pdfSplit` | PdfService.splitPdf() | file, pagesPerSplit(默认1) |
| `pdfMerge` | PdfService.mergePdf() | files[] (2-10个) |
| `pdfCompress` | PdfCompressService.compress() | file, level(1-5) |
| `pdfToImage` | PdfToImageService.convert() | file, format(png/jpeg/webp), dpi(默认150) |
| `docToPdf` | DocumentService.convertToPdf() | file (.doc/.docx/.wps) |
| `mdToDocx` | MarkdownService.mdToDocx() | file (.md) |

每个 Tool 方法内部：
1. 参数校验（格式/大小/数量）→ 抛出友好异常
2. 委托现有 Service 处理
3. 返回 ToolResult（含产物文件引用 + 统计信息）

### 4.3 ReActAgent 系统提示词

`classpath:prompts/doc-agent-system.md`:
```
你是文档处理助手。你可以帮用户处理 PDF/Word/WPS/Markdown 文件。

规则:
1. 用户上传文件后，主动询问要做什么操作（提供快捷选项）
2. 用户提出操作但缺文件时，提醒上传并说明支持的格式
3. 参数不明确时必须追问（如切分没给页数、压缩没给等级）
4. 处理完成后展示结果摘要，询问是否继续
5. 遇到错误时解释原因，给出具体建议
6. 不支持的操作诚实告知，不要编造能力
7. 始终以中文回复，语气友好简洁
```

### 4.4 ConversationManager

- 对话存储: 内存 Map（conversationId → List<Msg>），预留 Redis 扩展
- 文件生命周期: 对话结束后 30 分钟清理上传文件和处理产物
- 对话上限: 每个 conversation 最多 50 轮

---

## 5. API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/agent/chat` | 发送消息，SSE 流式返回 |
| `POST` | `/api/agent/upload` | 上传文件，返回 fileId |
| `GET` | `/api/agent/download/{fileId}` | 下载处理结果文件 |
| `GET` | `/api/agent/conversations` | 历史对话列表 |
| `GET` | `/api/agent/conversations/{id}` | 获取对话记录 |
| `DELETE` | `/api/agent/conversations/{id}` | 删除对话 |

### 5.1 POST /api/agent/chat

**请求**:
```json
{
  "message": "把这个 PDF 切成每 2 页一份",
  "fileIds": ["upload-uuid-001"],
  "conversationId": "conv-uuid-abc"
}
```

**响应** (SSE 流):
```
event: thinking
data: {"text":"正在分析你的需求..."}

event: tool_call
data: {"tool":"pdfSplit","params":"每 2 页一份"}

event: result
data: {"fileName":"report_split.zip","size":"14.8MB","fileId":"result-uuid-002"}

event: reply
data: {"text":"✅ 切分完成！共 60 个文件，14.8MB。"}

event: done
data: {}
```

### 5.2 SSE 事件类型

| event | 触发时机 | 前端效果 |
|-------|---------|---------|
| `thinking` | Agent 推理中 | 三点跳动动画 |
| `tool_call` | 调用工具 | "正在切分 PDF..." |
| `progress` | 处理进度（可选） | 进度条 |
| `result` | 处理完成 | 结果卡片+下载按钮 |
| `reply` | AI 文字回复 | 聊天气泡 |
| `error` | 处理异常 | 错误卡片+建议 |
| `done` | 本轮结束 | 输入框恢复 |

---

## 6. 异常处理设计

### 6.1 异常三层分类

| 层级 | 示例 | 处理策略 |
|------|------|---------|
| **用户层** | 格式不支持、文件过大、文件加密 | 友好提示 + 给出具体数字（如"50MB 上限"） |
| **操作层** | 参数不合理、数量超限、服务不可用 | 解释原因 + 替代建议（如"建议改为每 2 页"） |
| **系统层** | LLM 超时、磁盘不足、处理崩溃 | 兜底回复 + 后台告警 + 3 次自动重试 |

### 6.2 Agent 异常对话示例

```
用户: "把这张图片转成 PDF"
Agent: "文档助手暂不支持图片转 PDF，
       支持的操作有: PDF 切分/合并/压缩/转图片、
       文档转 PDF、Markdown 转 DOCX。
       你要试试这些吗？"

用户: [上传 80MB 的 WPS 文件]
Agent: "文件大小为 80MB，超过了 50MB 的上限。
       建议：压缩后重新上传，或拆分为多个小文件。"

用户: [要求切分 PDF，每份 500 页]
Agent: "这个 PDF 总共只有 120 页，
       每份 500 页只能产生 1 个文件，相当于没切分。
       要不要改成每 2 页或 5 页？"
```

### 6.3 系统异常兜底

```
LLM 调用超时（3 次重试后仍失败）:
  → "我正在处理，请稍等片刻..." + 前端 10s 后自动重试

磁盘空间不足:
  → "服务器存储空间不足，请稍后重试" + 后台告警

未知异常:
  → "抱歉，处理时遇到了问题，请稍后重试"
  → 后台记录完整堆栈，不暴露给用户
```

---

## 7. 文件变更清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `frontend/src/tools/doc-agent/index.vue` | 对话 Agent 前端组件 |
| `backend/.../controller/AgentController.java` | Agent 对话 Controller |
| `backend/.../service/agent/AgentService.java` | Agent 编排服务接口 |
| `backend/.../service/agent/impl/AgentServiceImpl.java` | Agent 编排实现 |
| `backend/.../service/agent/DocAgentToolkit.java` | 6 个 @Tool 封装 |
| `backend/.../service/agent/ConversationManager.java` | 对话管理 |
| `backend/.../service/agent/ErrorClassifier.java` | 异常分类处理 |
| `backend/.../config/DocAgentConfig.java` | AgentScope Bean 配置 |
| `backend/.../model/agent/ChatRequest.java` | 请求 DTO |
| `backend/.../model/agent/ChatEvent.java` | SSE 事件 DTO |
| `backend/src/main/resources/prompts/doc-agent-system.md` | Agent 系统提示词 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `backend/pom.xml` | 新增 AgentScope 依赖 |
| `backend/.../application.yml` | 新增 agentscope 配置段 |
| `backend/.../exception/ErrorCodeEnum.java` | 新增 Agent 相关错误码 |

---

## 8. 验收标准

1. **功能验收**
   - [ ] 用户可通过自然语言描述需求，Agent 正确识别意图
   - [ ] 6 个文档工具均可通过对话调用，结果正确
   - [ ] 支持三种交互模式：先传文件、先说需求、同时发送
   - [ ] 参数不明确时 Agent 主动追问
   - [ ] 异常情况下给出友好提示和重试建议

2. **测试验收**
   - [ ] AgentService 单元测试 ≥ 5 个用例（意图识别正确性）
   - [ ] DocAgentToolkit 集成测试 ≥ 6 个用例（每个工具一个）
   - [ ] 异常路径测试 ≥ 4 个用例（格式错误/文件过大/LLM超时/未知异常）
   - [ ] 前端构建通过 + 后端 mvn test 全绿

3. **非功能验收**
   - [ ] 对话响应 < 30s（含 LLM 推理 + 文件处理）
   - [ ] SSE 流式输出正常，无连接泄漏
   - [ ] 现有 6 个独立工具页面功能不受影响
