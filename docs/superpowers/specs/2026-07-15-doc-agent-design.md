# 文档处理 Agent 统一入口 — 设计文档

> 日期: 2026-07-15  |  状态: 设计确认  |  审查: 已修复 11 项

---

## 1. 概述

### 1.1 背景

当前 Toolbox 的文档工具（PDF 切分/合并/压缩/转图片 + 文档转 PDF + Markdown 转 DOCX）各为独立页面，用户需要逐个点击进入操作。

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
│  · 文件附件上传         │  (multipart   │     → ReActAgent            │
│  · 结果卡片下载         │   + SSE)      │       → DocAgentToolkit     │
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

- **meta**: `{ id: 'doc-agent', name: '文档助手', icon: '🤖', category: 'file' }`
- **菜单位置**: 侧边栏顶部（logo 下方）固定入口，不折叠在任何分组内，作为统一入口醒目展示
- **备注**: `group` 不设置，独立于 PDF 工具包。现有 PDF 工具保留在分组中作为快捷入口
- **布局**: 全屏对话界面（顶部标题栏 + 中间消息列表 + 底部输入区）

### 3.2 对话状态机

```
IDLE → (用户输入) → TEXT_ONLY / FILE_ONLY → READY → PROCESSING → DONE/ERROR/CANCELLED
                                  │                        │
                                  └── 缺参数则追问 ←────────┘
```

| 状态 | 说明 | 用户操作 |
|------|------|---------|
| IDLE | 进入页面，AI 主动打招呼 | 输入文字或上传文件 |
| TEXT_ONLY | 用户只发了文字 | AI 解析意图，缺文件追问 |
| FILE_ONLY | 用户只传了文件 | AI 追问操作意图 |
| READY | 意图+文件+参数齐全 | AI 确认参数，开始处理 |
| PROCESSING | 处理中，输入框锁定，显示取消按钮 | 可点击取消 |
| DONE | 处理完成 | 下载结果，继续对话 |
| ERROR | 处理异常 | 查看原因，按建议重试 |
| CANCELLED | 用户主动取消 | 回到 READY，可重新发起 |

**取消机制**: PROCESSING 状态下前端显示取消按钮 → 发送 `POST /api/agent/cancel` → 后端检查中断标志位 → 停止 LLM 推理 + 清理临时文件 → 返回 CANCELLED 状态。

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

### 3.5 前端 Composable 拆分

参考现有项目模式（`useClipboard`、`useToast`、`useTheme`），Agent 相关逻辑拆分为独立 composable：

| Composable | 职责 |
|------------|------|
| `useAgentChat` | 对话状态管理（消息列表、发送、接收、状态机） |
| `useSSE` | SSE 连接管理（创建、重连、心跳、断开） |
| `useFileUpload` | 文件拖拽/粘贴/点击上传 + 预览 |

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
  agent:
    max-steps: 8
    name: doc-assistant
  model:
    provider: dashscope
    model-name: qwen-plus
    api-key: ${DASHSCOPE_API_KEY}
```

> **注意**: `agentscope.*` 配置前缀以 AgentScope starter 实际的 `@ConfigurationProperties` 为准（可能是 `agentscope.agent.*` 而非 `agentscope.core.agent.*`）。实施时读取 starter 源码确认。

### 4.2 工具定义 (DocAgentToolkit)

6 个 `@Tool` 注解方法，每个封装一个现有 Service。**方法签名严格对齐现有 Service 接口**：

#### 4.2.1 pdfSplit

```java
@Tool(name = "pdfSplit", description = "拆分 PDF 文件")
public ToolResult pdfSplit(
    @ToolParam("PDF 文件") FileRef file,
    @ToolParam("拆分模式: by-page(逐页) / by-range(指定范围) / by-n(每N页)") String mode,
    @ToolParam("页码范围(mode=by-range 时使用), 如 '1,3,5-8'") String pages,
    @ToolParam("每N页(mode=by-n 时使用), 默认 1") int everyN);
```

→ 委托 `PdfService.splitPdf(bytes, filename, mode, pages, everyN, true)`

#### 4.2.2 pdfMerge

```java
@Tool(name = "pdfMerge", description = "合并多个 PDF 为一个文件，最多 10 个")
public ToolResult pdfMerge(
    @ToolParam("要合并的 PDF 文件列表，2-10 个") List<FileRef> files);
```

→ 委托 `PdfService.mergePdf(bytesList, true)`

#### 4.2.3 pdfCompress

```java
@Tool(name = "pdfCompress", description = "压缩 PDF 文件大小，level 1(极度)-5(极限画质)")
public ToolResult pdfCompress(
    @ToolParam("PDF 文件") FileRef file,
    @ToolParam("压缩等级 1-5，默认 3") int level);
```

→ 委托 `PdfCompressService.compress(bytes, filename, level)`

#### 4.2.4 pdfToImage

```java
@Tool(name = "pdfToImage", description = "将 PDF 页面转换为图片")
public ToolResult pdfToImage(
    @ToolParam("PDF 文件") FileRef file,
    @ToolParam("输出格式: png / jpeg / webp，默认 png") String format,
    @ToolParam("DPI 分辨率 72-600，默认 150") int dpi,
    @ToolParam("JPEG 质量 0.0-1.0，默认 0.9，仅 format=jpeg 时生效") float quality,
    @ToolParam("页码范围，如 '1-5'，不传=全部") String pageRange);
```

→ 委托 `PdfToImageService.convertToImages(bytes, filename, dpi, format, quality, pageRange)`

#### 4.2.5 docToPdf

```java
@Tool(name = "docToPdf", description = "将 Word/WPS 文档转换为 PDF")
public ToolResult docToPdf(
    @ToolParam("Word(.doc/.docx)或 WPS(.wps)文档") FileRef file);
```

→ 委托 `DocumentService.convertToPdf(bytes, filename)`

#### 4.2.6 mdToDocx

```java
@Tool(name = "mdToDocx", description = "将 Markdown 文本转换为 DOCX 文件")
public ToolResult mdToDocx(
    @ToolParam("Markdown 文本内容") String markdownContent,
    @ToolParam("输出文件名，不含扩展名") String outputName);
```

→ 委托 `MarkdownService.convertMarkdownToDocx(markdownContent)`

> **注意**: MarkdownService 接收的是文本字符串而非文件。用户可在对话中直接粘贴 Markdown 内容，Agent 提取文本后调用此工具。

### 4.3 ReActAgent 系统提示词

`classpath:prompts/doc-agent-system.md`:
```markdown
你是文档处理助手。你可以帮用户处理 PDF/Word/WPS/Markdown 文件。

## 文件限制速查（在用户提出不合理的需求时主动提醒）

| 工具 | 最多文件 | 单文件上限 | 允许格式 |
|------|---------|-----------|---------|
| PDF 切分 | 1 | 50MB | .pdf |
| PDF 合并 | 10 | 5MB | .pdf |
| PDF 压缩 | 1 | 50MB | .pdf |
| PDF 转图片 | 1 | 50MB | .pdf |
| 文档转 PDF | 5 | 50MB | .doc/.docx/.wps |
| Markdown 转 DOCX | — | — | 文本输入 |

## 可选参数速查（PDF 转图片 / PDF 压缩 有多个选项）

| 工具 | 可选参数 | 默认值 | 说明 |
|------|---------|--------|------|
| PDF 转图片 | format | png | png(无损大) / jpeg(有损小) / webp(平衡) |
| | dpi | 150 | 72(最小) / 150(清晰) / 300(高清) / 600(印刷级) |
| | quality | 0.9 | 仅 jpeg 生效: 0.7(小文件) / 0.9(平衡) / 1.0(最大) |
| | pageRange | 全部 | 如 "1-5" 或 "1,3,5" |
| PDF 压缩 | level | 3 | 1(极度) / 2(高度) / 3(推荐) / 4(轻度) / 5(极限画质) |

## 规则
1. 用户上传文件后，主动询问要做什么操作（提供快捷选项）
2. 用户提出操作但缺文件时，提醒上传并说明支持的格式和限制
3. **可选参数引导**: 有选项的工具（PDF 转图片、PDF 压缩），遵循"默认优先 + 按需询问":
   - 用户未提参数 → 使用默认值，告知用户"使用默认 png/150 DPI，需要调整吗？"
   - 用户提了部分参数 → 补全默认值，确认剩余参数
   - 用户明确要高质量 → 推荐 dpi=300 + format=png；用户要小文件 → 推荐 format=jpeg + quality=0.7
4. 参数不明确时必须追问（如切分没给页数/模式）
5. 用户提出超出限制的需求时（如合并 15 个文件），在对话中直接告知上限
6. 处理完成后展示结果摘要，询问是否继续
7. 遇到错误时解释原因，给出具体建议
8. 不支持的操作诚实告知，不要编造能力
9. 始终以中文回复，语气友好简洁
```

### 4.4 ConversationManager

- **对话存储**: 内存 `ConcurrentHashMap`（conversationId → List<Msg>）。**设计决策：一期不做持久化**，应用重启后对话历史丢失。理由是当前为单机部署、对话上下文短（每次操作独立），持久化需求不强烈。预留接口 `ConversationStore` 以便后续接入 Redis。
- **文件生命周期**: 对话结束后 30 分钟清理上传文件和处理产物（`ScheduledExecutorService` 定时扫描）。
- **对话上限**: 每个 conversation 最多 50 轮。
- **并发安全**: `ConcurrentHashMap` + 文件写入使用唯一 UUID 命名避免冲突。

---

## 5. API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/agent/chat` | 发送消息 + 上传文件（multipart），SSE 流式返回 |
| `POST` | `/api/agent/cancel` | 取消当前处理 |
| `GET` | `/api/agent/download/{fileId}` | 下载处理结果文件 |
| `GET` | `/api/agent/conversations` | 历史对话列表 |
| `GET` | `/api/agent/conversations/{id}` | 获取对话记录 |
| `DELETE` | `/api/agent/conversations/{id}` | 删除对话 |

> **设计决策**: 不设独立的 `/api/agent/upload` 端点。文件和消息统一通过 `/chat` 的 `multipart/form-data` 提交，与现有工具 Controller 模式一致（单次请求包含文件+参数），减少端点和鉴权面。

### 5.1 POST /api/agent/chat

**请求** (`multipart/form-data`):
```
message: "把这个 PDF 切成每 2 页一份"
files: [file1.pdf, file2.pdf]    (可选，MultipartFile[])
conversationId: "conv-uuid-abc"  (可选，新对话不传)
```

**响应** (`text/event-stream`):
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

### 5.2 SSE 事件类型与连接管理

| event | 触发时机 | 前端效果 |
|-------|---------|---------|
| `thinking` | Agent 推理中 | 三点跳动动画 |
| `tool_call` | 调用工具 | "正在切分 PDF..." |
| `progress` | 处理进度（可选） | 进度条 |
| `result` | 处理完成 | 结果卡片+下载按钮 |
| `reply` | AI 文字回复 | 聊天气泡 |
| `error` | 处理异常 | 错误卡片+建议 |
| `heartbeat` | 每 15s 心跳 | 无 UI 变化，维持连接 |
| `done` | 本轮结束 | 输入框恢复 |

**SSE 连接管理**:

| 机制 | 策略 |
|------|------|
| **心跳** | 后端每 15 秒发送 `event: heartbeat`，前端超时 30s 无事件则认为断连 |
| **断线重连** | 前端 `EventSource` 自动重连（默认 3s 间隔），手动重连 3 次后提示用户刷新 |
| **最大连接** | 后端限制同一 conversationId 只能有 1 个活跃 SSE 连接，新连接 push 旧连接 |
| **超时断开** | 处理完成后 5 分钟无新消息自动关闭 SSE 连接，释放资源 |
| **连接上限** | 全局最大 50 个并发 SSE 连接，超出返回 503 |

---

## 6. 异常处理设计

### 6.1 异常三层分类

| 层级 | 示例 | 处理策略 |
|------|------|---------|
| **用户层** | 格式不支持、文件过大、文件加密 | 友好提示 + 给出具体数字（如"50MB 上限"） |
| **操作层** | 参数不合理、数量超限、服务不可用 | 解释原因 + 替代建议（如"建议改为每 2 页"） |
| **系统层** | LLM 超时、磁盘不足、处理崩溃 | 兜底回复 + 后台告警 + 3 次自动重试 |

### 6.2 新增错误码 (ErrorCodeEnum)

```java
/** Agent LLM 调用超时 */
AGENT_LLM_TIMEOUT(500, "AI 服务响应超时，请稍后重试"),
/** Agent 无法理解用户意图 */
AGENT_INTENT_UNCLEAR(400, "无法理解您的需求，请更具体地描述"),
/** Agent 不支持的操作 */
AGENT_TOOL_NOT_FOUND(400, "暂不支持该操作"),
/** 服务器磁盘空间不足 */
AGENT_DISK_FULL(500, "服务器存储空间不足，请联系管理员"),
/** Agent 会话未找到 */
AGENT_SESSION_NOT_FOUND(404, "对话不存在或已过期"),
/** Agent 并发连接数超限 */
AGENT_TOO_MANY_CONNECTIONS(503, "当前使用人数较多，请稍后重试"),
```

遵循现有 `模块_错误类型` 命名规范。

### 6.3 Agent 对话示例

#### 可选参数引导（正向）

```
用户: [上传 report.pdf (30页)]
Agent: "你想对这个 PDF 做什么？"
       [切分] [合并] [压缩] [转图片]

用户: "转成图片"

Agent: "好的，使用默认设置: PNG 格式、150 DPI、全部页面。
       需要调整吗？你也可以选择:
       · 格式: PNG(无损) / JPEG(体积小) / WebP(平衡)
       · 清晰度: 72(最小) / 150(清晰) / 300(高清)
       · 范围: 全部 / 指定页(如 1-5)"

用户: "要 JPEG，高清一点的"

Agent: "好的，JPEG 格式 + 300 DPI + 90% 质量，共 30 页。
       开始转换？"

用户: "开始"

Agent: "⏳ 正在转换...
       ✅ 完成！30 张 JPEG 图片，共 12.5MB
       📦 report_images.zip [下载]
       还需要处理其他文件吗？"
```

#### 异常场景

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

### 6.4 系统异常兜底

```
LLM 调用超时（3 次重试后仍失败）:
  → "我正在处理，请稍等片刻..." + 前端 10s 后自动重试

磁盘空间不足:
  → "服务器存储空间不足，请稍后重试" + 后台告警 (log.error)

未知异常:
  → "抱歉，处理时遇到了问题，请稍后重试"
  → 后台记录完整堆栈 (log.error)，不暴露给用户
```

---

## 7. 文件变更清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `frontend/src/tools/doc-agent/index.vue` | 对话 Agent 前端组件 |
| `frontend/src/composables/useAgentChat.ts` | 对话状态管理 composable |
| `frontend/src/composables/useSSE.ts` | SSE 连接管理 composable |
| `backend/.../controller/AgentController.java` | Agent 对话 Controller |
| `backend/.../service/agent/AgentService.java` | Agent 编排服务接口 |
| `backend/.../service/agent/impl/AgentServiceImpl.java` | Agent 编排实现（含中断标志位） |
| `backend/.../service/agent/DocAgentToolkit.java` | 6 个 @Tool 封装 |
| `backend/.../service/agent/ConversationManager.java` | 对话管理 + ConversationStore 接口 |
| `backend/.../service/agent/ErrorClassifier.java` | 异常分类处理 |
| `backend/.../config/DocAgentConfig.java` | AgentScope Bean 配置 |
| `backend/.../model/agent/ChatRequest.java` | 请求 DTO |
| `backend/.../model/agent/ChatEvent.java` | SSE 事件 DTO |
| `backend/.../model/agent/ConversationStore.java` | 对话存储接口（预留 Redis） |
| `backend/src/main/resources/prompts/doc-agent-system.md` | Agent 系统提示词 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `backend/pom.xml` | 新增 AgentScope 依赖 |
| `backend/.../application.yml` | 新增 agentscope 配置段 |
| `backend/.../exception/ErrorCodeEnum.java` | 新增 6 个 Agent 错误码 |

---

## 8. 验收标准

1. **功能验收**
   - [ ] 用户可通过自然语言描述需求，Agent 正确识别意图
   - [ ] 6 个文档工具均可通过对话调用，结果正确
   - [ ] 支持三种交互模式：先传文件、先说需求、同时发送
   - [ ] 参数不明确时 Agent 主动追问
   - [ ] 异常情况下给出友好提示和重试建议
   - [ ] PROCESSING 状态下可取消，清理临时文件

2. **测试验收**
   - [ ] AgentService 单元测试 ≥ 5 个用例（意图识别正确性）
   - [ ] DocAgentToolkit 集成测试 ≥ 6 个用例（每个工具一个）
   - [ ] 异常路径测试 ≥ 6 个用例（格式错误/文件过大/LLM超时/未知异常/取消/并发限制）
   - [ ] 前端构建通过 + 后端 mvn test 全绿

3. **非功能验收**
   - [ ] 对话响应 < 30s（含 LLM 推理 + 文件处理）
   - [ ] SSE 流式输出正常，心跳 + 断线重连验证通过
   - [ ] 单 conversation 连接互斥，全局连接上限生效
   - [ ] 现有 6 个独立工具页面功能不受影响
