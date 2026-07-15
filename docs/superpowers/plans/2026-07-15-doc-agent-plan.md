# 文档处理 Agent 统一入口 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 AI 对话式文档处理 Agent 统一入口，生产级标准，通过自然语言交互调用 6 个现有文档工具。

**Architecture:** 后端新增 Agent 层（AgentScope ReActAgent + DocAgentToolkit），通过 SSE 流式返回对话结果；前端新增 doc-agent 组件，纯对话 UI。现有 Service 和独立工具页面零修改。

**Tech Stack:** AgentScope Java 1.0.12、Spring Boot 3.3、Spring MVC SseEmitter、DashScope qwen-plus、Vue 3 + TypeScript

**Source Spec:** `docs/superpowers/specs/2026-07-15-doc-agent-design.md`

## Global Constraints

- JDK 17，Spring Boot 3.3.0，不得降级或升级
- AgentScope 版本: `io.agentscope:agentscope-spring-boot-starter:1.0.12`
- LLM API Key 通过环境变量 `${DASHSCOPE_API_KEY}` 注入，禁止硬编码
- 现有 6 个文档工具的 Service 接口和独立页面不得修改
- 后端日志: `log.info("[ClassName#methodName] description {}", var)`
- Controller 使用构造函数注入，禁止 `@Autowired` 字段注入
- SSE 最大并发连接: 50；单 conversation 连接互斥
- 文件临时目录: `java.io.tmpdir/toolbox-agent/`，30 分钟定时清理
- 前端 CSS 仅使用 `var(--*)` 自定义属性，禁止硬编码颜色值
- Docker 镜像基于 `eclipse-temurin:17-jre-jammy`，不新增系统依赖

---

## File Map

```
新增后端文件 (14):
  backend/src/main/java/com/toolbox/
  ├── config/DocAgentConfig.java          # AgentScope Bean 配置
  ├── controller/AgentController.java     # SSE 流式对话 Controller
  ├── service/agent/
  │   ├── AgentService.java               # 编排服务接口
  │   ├── impl/AgentServiceImpl.java      # 编排实现（ReActAgent 运行）
  │   ├── DocAgentToolkit.java            # 6 个 @Tool 方法
  │   ├── ConversationManager.java        # 对话生命周期管理
  │   ├── FileManager.java                # 文件上传/临时清理
  │   └── SseConnectionManager.java       # SSE 连接池 + 并发控制
  └── model/agent/
      ├── ChatRequest.java                # 请求 DTO
      ├── ChatEvent.java                  # SSE 事件 DTO
      ├── ConversationStore.java          # 对话存储接口
      └── InMemoryConversationStore.java  # 内存实现（一期）

新增资源文件 (1):
  backend/src/main/resources/prompts/doc-agent-system.md

新增前端文件 (3):
  frontend/src/tools/doc-agent/index.vue
  frontend/src/composables/useSSE.ts
  frontend/src/composables/useAgentChat.ts

修改现有文件 (3):
  backend/pom.xml                          # 新增 AgentScope 依赖
  backend/src/main/resources/application.yml  # 新增 agentscope 配置段
  backend/src/main/java/com/toolbox/exception/ErrorCodeEnum.java  # +6 错误码
```

---

## Phase 1: Foundation — 依赖、配置、基础设施

### Task 1: Maven 依赖 + application.yml 配置 + ErrorCodeEnum 扩展

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/toolbox/exception/ErrorCodeEnum.java`

**Interfaces:**
- Produces: 6 个 Agent 错误码常量 + agentscope 配置属性前缀
- Produces: `ErrorCodeEnum.AGENT_LLM_TIMEOUT(500)` 等供后续 Task 使用

- [ ] **Step 1: 添加 AgentScope Maven 依赖**

在 `backend/pom.xml` 的 `<dependencies>` 末尾（`</dependencies>` 之前）添加:

```xml
<!-- AgentScope AI Agent 框架 -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-spring-boot-starter</artifactId>
    <version>1.0.12</version>
</dependency>
```

- [ ] **Step 2: 添加 application.yml Agent 配置**

在 `backend/src/main/resources/application.yml` 末尾追加:

```yaml
toolbox:
  agent:
    # LLM 提供商: dashscope / openai / deepseek
    llm-provider: dashscope
    llm-model: qwen-plus
    # API Key 从环境变量注入，开发机可临时在下面覆盖
    llm-api-key: ${DASHSCOPE_API_KEY:}
    # SSE 连接管理
    sse:
      max-connections: 50
      heartbeat-interval-ms: 15000
      connection-timeout-ms: 300000
    # 对话管理
    conversation:
      max-rounds: 50
      ttl-minutes: 30
    # 文件管理
    file:
      upload-dir: ${java.io.tmpdir}/toolbox-agent
      cleanup-interval-minutes: 10
      max-file-size: 52428800
```

- [ ] **Step 3: 扩展 ErrorCodeEnum**

在 `ErrorCodeEnum.java` 的 `PDF_COMPRESS_PROCESS_ERROR` 条目后、`;` 之前追加 6 个 Agent 错误码:

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
AGENT_TOO_MANY_CONNECTIONS(503, "当前使用人数较多，请稍后重试");
```

- [ ] **Step 4: 验证编译通过**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml \
        backend/src/main/java/com/toolbox/exception/ErrorCodeEnum.java
git commit -m "feat(agent): 添加 AgentScope 依赖、配置和错误码

- agentscope-spring-boot-starter 1.0.12
- toolbox.agent.* 配置段（LLM/SSE/对话/文件管理）
- 6 个 Agent 错误码"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 2: DTO — ChatRequest、ChatEvent、ConversationStore

**Files:**
- Create: `backend/src/main/java/com/toolbox/model/agent/ChatRequest.java`
- Create: `backend/src/main/java/com/toolbox/model/agent/ChatEvent.java`
- Create: `backend/src/main/java/com/toolbox/model/agent/ConversationStore.java`

**Interfaces:**
- Produces: `ChatRequest` — 供 Controller、AgentService 使用
- Produces: `ChatEvent` — 供 AgentService、Controller SSE 使用
- Produces: `ConversationStore` 接口 — 供 ConversationManager、InMemoryConversationStore 实现

- [ ] **Step 1: 编写 ChatRequest.java**

```java
package com.toolbox.model.agent;

import org.springframework.web.multipart.MultipartFile;

/**
 * Agent 对话请求 DTO
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class ChatRequest {

    /** 用户消息文本 */
    private String message;

    /** 上传的文件列表（可选） */
    private MultipartFile[] files;

    /** 对话 ID（可选，新对话不传） */
    private String conversationId;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public MultipartFile[] getFiles() { return files; }
    public void setFiles(MultipartFile[] files) { this.files = files; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
}
```

- [ ] **Step 2: 编写 ChatEvent.java**

```java
package com.toolbox.model.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * SSE 事件 DTO — 序列化为 JSON 发送给前端
 *
 * @author toolbox
 * @since 2026-07-15
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatEvent {

    public enum Type {
        thinking, tool_call, progress, result, reply, error, heartbeat, done
    }

    private Type type;
    private String text;
    private String tool;
    private String params;
    private String fileName;
    private String fileId;
    private String size;
    private int progress;
    private Map<String, Object> extra;

    // --- 工厂方法 ---

    public static ChatEvent thinking(String text) {
        ChatEvent e = new ChatEvent();
        e.type = Type.thinking;
        e.text = text;
        return e;
    }

    public static ChatEvent toolCall(String tool, String params) {
        ChatEvent e = new ChatEvent();
        e.type = Type.tool_call;
        e.tool = tool;
        e.params = params;
        return e;
    }

    public static ChatEvent result(String fileName, String fileId, String size) {
        ChatEvent e = new ChatEvent();
        e.type = Type.result;
        e.fileName = fileName;
        e.fileId = fileId;
        e.size = size;
        return e;
    }

    public static ChatEvent reply(String text) {
        ChatEvent e = new ChatEvent();
        e.type = Type.reply;
        e.text = text;
        return e;
    }

    public static ChatEvent error(String text) {
        ChatEvent e = new ChatEvent();
        e.type = Type.error;
        e.text = text;
        return e;
    }

    public static ChatEvent heartbeat() {
        ChatEvent e = new ChatEvent();
        e.type = Type.heartbeat;
        return e;
    }

    public static ChatEvent done() {
        ChatEvent e = new ChatEvent();
        e.type = Type.done;
        return e;
    }

    public static ChatEvent progress(int pct) {
        ChatEvent e = new ChatEvent();
        e.type = Type.progress;
        e.progress = pct;
        return e;
    }

    // --- getters ---

    public Type getType() { return type; }
    public String getText() { return text; }
    public String getTool() { return tool; }
    public String getParams() { return params; }
    public String getFileName() { return fileName; }
    public String getFileId() { return fileId; }
    public String getSize() { return size; }
    public int getProgress() { return progress; }
    public Map<String, Object> getExtra() { return extra; }
}
```

- [ ] **Step 3: 编写 ConversationStore.java**

```java
package com.toolbox.model.agent;

import java.util.List;
import java.util.Optional;

/**
 * 对话存储抽象接口 — 一期内存实现，预留 Redis 扩展
 *
 * @author toolbox
 * @since 2026-07-15
 */
public interface ConversationStore {

    /** 创建新对话，返回 conversationId */
    String create();

    /** 追加消息到对话 */
    void append(String conversationId, ConversationMessage message);

    /** 获取对话全部消息 */
    List<ConversationMessage> getMessages(String conversationId);

    /** 查找对话 */
    Optional<ConversationEntry> findById(String conversationId);

    /** 删除对话 */
    void delete(String conversationId);

    /** 列出所有活跃对话 */
    List<ConversationEntry> listActive();

    /**
     * 单条对话消息
     */
    record ConversationMessage(
        String role,        // "user" | "assistant" | "system"
        String content,     // 文本内容
        List<String> fileIds, // 关联文件 ID
        long timestamp
    ) {}

    /**
     * 对话摘要条目
     */
    record ConversationEntry(
        String conversationId,
        String title,          // 首条用户消息的前 50 字
        int roundCount,
        long createdAt,
        long lastActiveAt
    ) {}
}
```

- [ ] **Step 4: 编译验证**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/toolbox/model/agent/
git commit -m "feat(agent): 添加 ChatRequest、ChatEvent DTO 和 ConversationStore 接口"
```

---

### Task 3: FileManager — 文件生命周期管理

**Files:**
- Create: `backend/src/main/java/com/toolbox/service/agent/FileManager.java`

**Interfaces:**
- Consumes: `toolbox.agent.file.*` 配置属性（来自 Task 1）
- Produces: `FileManager.store(MultipartFile) → String fileId`、`FileManager.load(fileId) → File`、`FileManager.cleanup()` 供 AgentService、AgentController 使用

- [ ] **Step 1: 编写 FileManager 测试**

```java
// backend/src/test/java/com/toolbox/service/agent/FileManagerTest.java
package com.toolbox.service.agent;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileManagerTest {

    private FileManager fileManager;
    private Path uploadDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        uploadDir = tempDir.resolve("toolbox-agent");
        fileManager = new FileManager(uploadDir.toString(), 52428800L,
                java.time.Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("store saves file and returns fileId")
    void store_shouldReturnFileId() throws Exception {
        MockMultipartFile mpf = new MockMultipartFile(
            "file", "test.pdf", "application/pdf", "hello pdf".getBytes());

        String fileId = fileManager.store(mpf);

        assertNotNull(fileId);
        assertTrue(fileId.length() > 0);
        assertTrue(new File(uploadDir.toFile(), fileId).exists());
    }

    @Test
    @DisplayName("load returns stored file")
    void load_shouldReturnStoredFile() throws Exception {
        MockMultipartFile mpf = new MockMultipartFile(
            "file", "test.pdf", "application/pdf", "content".getBytes());
        String fileId = fileManager.store(mpf);

        File loaded = fileManager.load(fileId);

        assertNotNull(loaded);
        assertEquals("content".length(), loaded.length());
    }

    @Test
    @DisplayName("store rejects file exceeding max size")
    void store_shouldRejectOversizedFile() {
        byte[] large = new byte[52428801]; // 50MB + 1 byte
        MockMultipartFile mpf = new MockMultipartFile(
            "file", "big.pdf", "application/pdf", large);

        assertThrows(IllegalArgumentException.class, () -> fileManager.store(mpf));
    }

    @Test
    @DisplayName("cleanup removes expired files")
    void cleanup_shouldRemoveExpiredFiles() throws Exception {
        // 创建一个"过期"文件（直接放到目录里）
        File expired = new File(uploadDir.toFile(), "expired-file");
        uploadDir.toFile().mkdirs();
        expired.createNewFile();
        expired.setLastModified(System.currentTimeMillis() - 31 * 60 * 1000);

        fileManager.cleanup();

        assertFalse(expired.exists());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
cd backend && mvn test -pl . -Dtest=FileManagerTest
```

Expected: FAIL — FileManager 类不存在

- [ ] **Step 3: 实现 FileManager**

```java
package com.toolbox.service.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

/**
 * 文件生命周期管理 — 上传存储、读取、定时清理
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class FileManager {

    private static final Logger log = LoggerFactory.getLogger(FileManager.class);

    private final Path uploadDir;
    private final long maxFileSize;
    private final Duration ttl;

    public FileManager(String uploadDirPath, long maxFileSize, Duration ttl) {
        this.uploadDir = Path.of(uploadDirPath);
        this.maxFileSize = maxFileSize;
        this.ttl = ttl;
        init();
    }

    /**
     * 初始化上传目录
     */
    private void init() {
        try {
            Files.createDirectories(uploadDir);
            log.info("[FileManager#init] upload directory created: {}", uploadDir);
        } catch (IOException e) {
            log.error("[FileManager#init] failed to create upload directory: {}", uploadDir, e);
            throw new RuntimeException("无法创建上传目录", e);
        }
    }

    /**
     * 存储上传文件
     *
     * @param file MultipartFile
     * @return fileId 唯一文件标识
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("文件过大: " + file.getSize() + " > " + maxFileSize);
        }

        String fileId = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        Path target = uploadDir.resolve(fileId + ext);

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            log.info("[FileManager#store] file stored: {} → {}", originalName, fileId + ext);
            return fileId + ext;
        } catch (IOException e) {
            log.error("[FileManager#store] failed to store file: {}", originalName, e);
            throw new RuntimeException("文件存储失败", e);
        }
    }

    /**
     * 加载存储的文件
     *
     * @param fileId 文件标识
     * @return File 对象
     */
    public File load(String fileId) {
        File file = uploadDir.resolve(fileId).toFile();
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + fileId);
        }
        return file;
    }

    /**
     * 删除指定文件
     *
     * @param fileId 文件标识
     */
    public void delete(String fileId) {
        try {
            Files.deleteIfExists(uploadDir.resolve(fileId));
            log.info("[FileManager#delete] file deleted: {}", fileId);
        } catch (IOException e) {
            log.warn("[FileManager#delete] failed to delete file: {}", fileId, e);
        }
    }

    /**
     * 清理过期文件（由定时任务调用）
     */
    public void cleanup() {
        File dir = uploadDir.toFile();
        File[] files = dir.listFiles();
        if (files == null) return;

        long now = System.currentTimeMillis();
        long ttlMs = ttl.toMillis();
        int count = 0;

        for (File file : files) {
            if (now - file.lastModified() > ttlMs) {
                if (file.delete()) count++;
            }
        }
        if (count > 0) {
            log.info("[FileManager#cleanup] cleaned {} expired files", count);
        }
    }

    public Path getUploadDir() { return uploadDir; }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
cd backend && mvn test -Dtest=FileManagerTest
```

Expected: Tests run: 4, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/toolbox/service/agent/FileManager.java \
        backend/src/test/java/com/toolbox/service/agent/FileManagerTest.java
git commit -m "feat(agent): 添加 FileManager 文件生命周期管理（含测试）"
```

---

### Task 4: SseConnectionManager — SSE 连接池 + 并发控制

**Files:**
- Create: `backend/src/main/java/com/toolbox/service/agent/SseConnectionManager.java`

**Interfaces:**
- Consumes: `toolbox.agent.sse.*` 配置属性（来自 Task 1）
- Produces: `SseConnectionManager.register(convId, emitter) → boolean`、`unregister(convId)`、`getActiveCount() → int` 供 AgentController 使用

- [ ] **Step 1: 编写 SseConnectionManager 测试**

```java
// backend/src/test/java/com/toolbox/service/agent/SseConnectionManagerTest.java
package com.toolbox.service.agent;

import org.junit.jupiter.api.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SseConnectionManagerTest {

    private SseConnectionManager manager;

    @BeforeEach
    void setUp() {
        manager = new SseConnectionManager(3, 15000L, 300000L);
    }

    @Test
    @DisplayName("register succeeds when under limit")
    void register_shouldSucceed() {
        SseEmitter emitter = new SseEmitter();
        boolean ok = manager.register("conv-1", emitter);
        assertTrue(ok);
        assertEquals(1, manager.getActiveCount());
    }

    @Test
    @DisplayName("register fails when connection already exists for same conversation")
    void register_shouldFailForDuplicateConversation() {
        SseEmitter e1 = new SseEmitter();
        SseEmitter e2 = new SseEmitter();
        assertTrue(manager.register("conv-1", e1));
        assertFalse(manager.register("conv-1", e2));
    }

    @Test
    @DisplayName("register fails when over max connections limit")
    void register_shouldFailWhenOverLimit() throws Exception {
        // 填满连接池
        for (int i = 0; i < 3; i++) {
            assertTrue(manager.register("conv-" + i, new SseEmitter()));
        }
        // 第 4 个应该失败
        assertFalse(manager.register("conv-over", new SseEmitter()));
        assertEquals(3, manager.getActiveCount());
    }

    @Test
    @DisplayName("unregister frees connection slot")
    void unregister_shouldFreeSlot() {
        manager.register("conv-1", new SseEmitter());
        manager.unregister("conv-1");
        assertEquals(0, manager.getActiveCount());
        // 现在可以重新注册
        assertTrue(manager.register("conv-1", new SseEmitter()));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
cd backend && mvn test -Dtest=SseConnectionManagerTest
```

- [ ] **Step 3: 实现 SseConnectionManager**

```java
package com.toolbox.service.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 连接池管理 — 并发控制、连接互斥、心跳
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class SseConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(SseConnectionManager.class);

    private final int maxConnections;
    private final long heartbeatIntervalMs;
    private final long connectionTimeoutMs;

    /** conversationId → SseEmitter */
    private final ConcurrentHashMap<String, SseEmitter> connections = new ConcurrentHashMap<>();

    public SseConnectionManager(int maxConnections, long heartbeatIntervalMs,
                                 long connectionTimeoutMs) {
        this.maxConnections = maxConnections;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    /**
     * 注册 SSE 连接。单 conversation 互斥，超出上限拒绝。
     *
     * @param conversationId 对话 ID
     * @param emitter        SseEmitter
     * @return true 注册成功，false 被拒绝（已有连接或超出上限）
     */
    public boolean register(String conversationId, SseEmitter emitter) {
        // 全局连接数检查
        if (connections.size() >= maxConnections) {
            log.warn("[SseConnectionManager#register] connection pool full: {} >= {}",
                    connections.size(), maxConnections);
            return false;
        }

        // 单 conversation 互斥: 如果已有活跃连接则拒绝新连接
        SseEmitter existing = connections.putIfAbsent(conversationId, emitter);
        if (existing != null) {
            log.info("[SseConnectionManager#register] conversation {} already has active " +
                    "connection, rejecting new", conversationId);
            return false;
        }

        // 连接关闭时自动清理
        emitter.onCompletion(() -> {
            connections.remove(conversationId);
            log.info("[SseConnectionManager#onCompletion] connection closed: {}", conversationId);
        });
        emitter.onTimeout(() -> {
            connections.remove(conversationId);
            log.info("[SseConnectionManager#onTimeout] connection timeout: {}", conversationId);
        });
        emitter.onError(ex -> {
            connections.remove(conversationId);
            log.warn("[SseConnectionManager#onError] connection error: {}", conversationId, ex);
        });

        log.info("[SseConnectionManager#register] registered: {} (active: {})",
                conversationId, connections.size());
        return true;
    }

    /**
     * 主动注销连接
     */
    public void unregister(String conversationId) {
        SseEmitter emitter = connections.remove(conversationId);
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    /** 当前活跃连接数 */
    public int getActiveCount() { return connections.size(); }

    /** 心跳间隔 ms */
    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }

    /** 连接超时 ms */
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
cd backend && mvn test -Dtest=SseConnectionManagerTest
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/toolbox/service/agent/SseConnectionManager.java \
        backend/src/test/java/com/toolbox/service/agent/SseConnectionManagerTest.java
git commit -m "feat(agent): 添加 SseConnectionManager SSE 连接池管理（含测试）"
```

---

### Task 5: InMemoryConversationStore + ConversationManager

**Files:**
- Create: `backend/src/main/java/com/toolbox/model/agent/InMemoryConversationStore.java`
- Create: `backend/src/main/java/com/toolbox/service/agent/ConversationManager.java`

**Interfaces:**
- Consumes: `ConversationStore` 接口（来自 Task 2）、`toolbox.agent.conversation.*` 配置（来自 Task 1）
- Produces: `ConversationManager.create() → String`、`append()`、`getMessages()`、`delete()` 供 AgentService、AgentController 使用

- [ ] **Step 1: 实现 InMemoryConversationStore**

```java
package com.toolbox.model.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现对话存储 — 一期方案，应用重启后数据丢失
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class InMemoryConversationStore implements ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryConversationStore.class);

    private final ConcurrentHashMap<String, ConversationEntry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ConversationMessage>> messages = new ConcurrentHashMap<>();

    @Override
    public String create() {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        entries.put(id, new ConversationEntry(id, "新对话", 0, now, now));
        messages.put(id, Collections.synchronizedList(new ArrayList<>()));
        log.info("[InMemoryConversationStore#create] conversation created: {}", id);
        return id;
    }

    @Override
    public void append(String conversationId, ConversationMessage message) {
        List<ConversationMessage> msgs = messages.get(conversationId);
        if (msgs == null) return;
        msgs.add(message);

        // 更新摘要
        ConversationEntry entry = entries.get(conversationId);
        if (entry != null) {
            entries.put(conversationId, new ConversationEntry(
                conversationId,
                entry.title(),
                msgs.size(),
                entry.createdAt(),
                System.currentTimeMillis()
            ));
        }
    }

    @Override
    public List<ConversationMessage> getMessages(String conversationId) {
        List<ConversationMessage> msgs = messages.get(conversationId);
        return msgs != null ? Collections.unmodifiableList(msgs) : List.of();
    }

    @Override
    public Optional<ConversationEntry> findById(String conversationId) {
        return Optional.ofNullable(entries.get(conversationId));
    }

    @Override
    public void delete(String conversationId) {
        entries.remove(conversationId);
        messages.remove(conversationId);
        log.info("[InMemoryConversationStore#delete] conversation deleted: {}", conversationId);
    }

    @Override
    public List<ConversationEntry> listActive() {
        return List.copyOf(entries.values());
    }
}
```

- [ ] **Step 2: 实现 ConversationManager**

```java
package com.toolbox.service.agent;

import com.toolbox.model.agent.ConversationStore;
import com.toolbox.model.agent.ConversationStore.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 对话生命周期管理 — 创建、追加消息、对话上限控制
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class ConversationManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationManager.class);

    private final ConversationStore store;
    private final int maxRounds;

    public ConversationManager(ConversationStore store, int maxRounds) {
        this.store = store;
        this.maxRounds = maxRounds;
    }

    /**
     * 创建新对话
     */
    public String create() {
        return store.create();
    }

    /**
     * 追加用户消息
     */
    public void appendUserMessage(String conversationId, String message,
                                   List<String> fileIds) {
        checkRoundLimit(conversationId);
        store.append(conversationId, new ConversationMessage(
            "user", message, fileIds, System.currentTimeMillis()));
    }

    /**
     * 追加助手消息
     */
    public void appendAssistantMessage(String conversationId, String message) {
        store.append(conversationId, new ConversationMessage(
            "assistant", message, List.of(), System.currentTimeMillis()));
    }

    /**
     * 获取对话历史
     */
    public List<ConversationMessage> getHistory(String conversationId) {
        return store.getMessages(conversationId);
    }

    /**
     * 删除对话
     */
    public void delete(String conversationId) {
        store.delete(conversationId);
    }

    /**
     * 对话是否存在
     */
    public boolean exists(String conversationId) {
        return store.findById(conversationId).isPresent();
    }

    /**
     * 检查对话轮次上限
     */
    private void checkRoundLimit(String conversationId) {
        List<ConversationMessage> msgs = store.getMessages(conversationId);
        if (msgs == null) return;
        long userMsgCount = msgs.stream().filter(m -> "user".equals(m.role())).count();
        if (userMsgCount >= maxRounds) {
            log.warn("[ConversationManager#checkRoundLimit] conversation {} reached max " +
                    "rounds: {}", conversationId, maxRounds);
            throw new IllegalStateException("对话轮次已达上限（" + maxRounds + "轮），请开启新对话");
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/toolbox/model/agent/InMemoryConversationStore.java \
        backend/src/main/java/com/toolbox/service/agent/ConversationManager.java
git commit -m "feat(agent): 添加 InMemoryConversationStore 和 ConversationManager"
```

---

## Phase 2: Agent Core — AgentScope 集成 + 工具封装

### Task 6: 系统提示词文件

**Files:**
- Create: `backend/src/main/resources/prompts/doc-agent-system.md`

- [ ] **Step 1: 创建 prompts 目录并编写提示词**

```bash
mkdir -p backend/src/main/resources/prompts
```

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

## 可选参数速查（所有工具的选项和默认值）

| 工具 | 可选参数 | 默认值 | 可选值 |
|------|---------|--------|--------|
| **PDF 切分** | mode | by-page | by-page(逐页拆) / by-range(指定范围) / by-n(每N页一组) |
| | pages | — | mode=by-range 时: 如 "1,3,5-8" |
| | everyN | 1 | mode=by-n 时: 常用 2/5/10 |
| **PDF 压缩** | level | 3 | 1(极度:72dpi) / 2(高度:100dpi) / 3(推荐:150dpi) / 4(轻度:200dpi) / 5(极限:300dpi) |
| **PDF 转图片** | format | png | png(无损大) / jpeg(有损小) / webp(平衡) |
| | dpi | 150 | 72(最小) / 150(清晰) / 300(高清) / 600(印刷级) |
| | quality | 0.9 | 仅 jpeg: 0.7(小文件) / 0.9(平衡) / 1.0(最大) |
| | pageRange | 全部 | 如 "1-5" 或 "1,3,5" |

## 规则
1. 用户上传文件后，主动询问要做什么操作（提供快捷选项: 切分/合并/压缩/转图片/转PDF）
2. 用户提出操作但缺文件时，提醒上传并说明支持的格式和限制
3. **可选参数引导（适用所有有选项的工具）**, 遵循"默认优先 + 按需询问":
   - 用户未提参数 → 使用默认值, 告知用户使用的默认设置 + 一句话提示可调整
   - 用户提了部分参数 → 补全默认值, 确认剩余参数
   - 根据用户场景智能推荐:
     · "要清晰" → 高 DPI + png；"要小文件" → 低 DPI + jpeg
     · "简单快速" → by-page 逐页；"只要几页" → by-range
     · "尽量压缩" → level 1/2；"保持质量" → level 4/5
4. 参数不明确时必须追问（如切分没给模式、压缩没给等级、转图片没给 DPI）
5. 用户提出超出限制的需求时（如合并 15 个文件），在对话中直接告知上限
6. 处理完成后展示结果摘要，询问是否继续
7. 遇到错误时解释原因，给出具体建议
8. 不支持的操作诚实告知，不要编造能力
9. 始终以中文回复，语气友好简洁
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/prompts/
git commit -m "feat(agent): 添加 Agent 系统提示词文件"
```

---

### Task 7: DocAgentConfig — AgentScope Bean 配置

**Files:**
- Create: `backend/src/main/java/com/toolbox/config/DocAgentConfig.java`

**Interfaces:**
- Consumes: `toolbox.agent.*`、`toolbox.libreoffice.*` 配置属性 + Task 2-5 的类
- Produces: Spring Beans — `FileManager`、`SseConnectionManager`、`ConversationStore`、`ConversationManager`、`DocAgentToolkit`、`ReActAgent`（AgentScope）、`Model`（LLM）

- [ ] **Step 1: 实现 DocAgentConfig**

```java
package com.toolbox.config;

import com.toolbox.model.agent.ConversationStore;
import com.toolbox.model.agent.InMemoryConversationStore;
import com.toolbox.service.agent.*;
import com.toolbox.service.document.DocumentService;
import com.toolbox.service.markdown.MarkdownService;
import com.toolbox.service.pdf.*;
import io.agentscope.core.agent.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * AgentScope Bean 配置 — 组装 Agent 所需全部组件
 *
 * @author toolbox
 * @since 2026-07-15
 */
@Configuration
public class DocAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(DocAgentConfig.class);

    // --- LLM 配置 ---
    @Value("${toolbox.agent.llm-provider:dashscope}")
    private String llmProvider;

    @Value("${toolbox.agent.llm-model:qwen-plus}")
    private String llmModel;

    @Value("${toolbox.agent.llm-api-key:}")
    private String llmApiKey;

    // --- SSE 配置 ---
    @Value("${toolbox.agent.sse.max-connections:50}")
    private int sseMaxConnections;

    @Value("${toolbox.agent.sse.heartbeat-interval-ms:15000}")
    private long sseHeartbeatMs;

    @Value("${toolbox.agent.sse.connection-timeout-ms:300000}")
    private long sseTimeoutMs;

    // --- 对话配置 ---
    @Value("${toolbox.agent.conversation.max-rounds:50}")
    private int conversationMaxRounds;

    @Value("${toolbox.agent.conversation.ttl-minutes:30}")
    private int conversationTtlMinutes;

    // --- 文件配置 ---
    @Value("${toolbox.agent.file.upload-dir:${java.io.tmpdir}/toolbox-agent}")
    private String fileUploadDir;

    @Value("${toolbox.agent.file.max-file-size:52428800}")
    private long fileMaxSize;

    // ===== Bean 定义 =====

    @Bean
    public FileManager fileManager() {
        return new FileManager(fileUploadDir, fileMaxSize,
                Duration.ofMinutes(conversationTtlMinutes));
    }

    @Bean
    public SseConnectionManager sseConnectionManager() {
        return new SseConnectionManager(sseMaxConnections, sseHeartbeatMs, sseTimeoutMs);
    }

    @Bean
    public ConversationStore conversationStore() {
        return new InMemoryConversationStore();
    }

    @Bean
    public ConversationManager conversationManager(ConversationStore store) {
        return new ConversationManager(store, conversationMaxRounds);
    }

    @Bean
    public DocAgentToolkit docAgentToolkit(
            PdfService pdfService,
            PdfCompressService pdfCompressService,
            PdfToImageService pdfToImageService,
            DocumentService documentService,
            MarkdownService markdownService,
            FileManager fileManager) {
        return new DocAgentToolkit(pdfService, pdfCompressService, pdfToImageService,
                documentService, markdownService, fileManager);
    }

    @Bean
    public Model agentModel() {
        if (llmApiKey == null || llmApiKey.isBlank()) {
            log.warn("[DocAgentConfig#agentModel] LLM API Key not configured, " +
                    "agent will be unavailable");
        }
        // AgentScope Model 工厂 — 根据 provider 创建对应的 Model
        // 生产环境使用 DashScope，开发/演示环境可切换
        return Model.builder()
                .provider(llmProvider)
                .modelName(llmModel)
                .apiKey(llmApiKey)
                .build();
    }

    @Bean
    public ReActAgent docAgent(Model model, DocAgentToolkit toolkit) {
        String sysPrompt = loadSystemPrompt();
        return ReActAgent.builder()
                .name("doc-assistant")
                .systemPrompt(sysPrompt)
                .model(model)
                .toolkit(new Toolkit(toolkit))
                .maxSteps(8)
                .build();
    }

    /**
     * 加载系统提示词文件
     */
    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/doc-agent-system.md");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[DocAgentConfig#loadSystemPrompt] failed to load system prompt", e);
            return "你是文档处理助手。";
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
```

Expected: 首次编译可能出现 AgentScope API 方法不匹配的编译错误，根据实际 API 调整 `Model.builder()` 和 `ReActAgent.builder()` 的链式调用。

- [ ] **Step 3: 根据 AgentScope 实际 API 调整**

如果编译失败，运行以下命令查看 AgentScope starter 提供的实际类:

```bash
cd backend && mvn dependency:tree | grep agentscope
```

然后查阅 `io.agentscope` 包下的 `Model`、`ReActAgent`、`Toolkit` 类的实际构造方法，调整 DocAgentConfig 中的 Bean 定义。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/toolbox/config/DocAgentConfig.java
git commit -m "feat(agent): 添加 DocAgentConfig AgentScope Bean 配置"
```

---

### Task 8: DocAgentToolkit — 6 个 @Tool 方法

**Files:**
- Create: `backend/src/main/java/com/toolbox/service/agent/DocAgentToolkit.java`

**Interfaces:**
- Consumes: 5 个现有 Service 接口 + FileManager
- Produces: 6 个 @Tool 方法（pdfSplit/pdfMerge/pdfCompress/pdfToImage/docToPdf/mdToDocx），每个返回 `String`（AgentScope Tool 结果）

- [ ] **Step 1: 实现 DocAgentToolkit**

```java
package com.toolbox.service.agent;

import com.toolbox.service.document.DocumentService;
import com.toolbox.service.markdown.MarkdownService;
import com.toolbox.service.pdf.*;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Agent 工具箱 — 6 个 @Tool 方法封装现有文档处理 Service
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class DocAgentToolkit {

    private static final Logger log = LoggerFactory.getLogger(DocAgentToolkit.class);

    private final PdfService pdfService;
    private final PdfCompressService pdfCompressService;
    private final PdfToImageService pdfToImageService;
    private final DocumentService documentService;
    private final MarkdownService markdownService;
    private final FileManager fileManager;

    public DocAgentToolkit(PdfService pdfService, PdfCompressService pdfCompressService,
                           PdfToImageService pdfToImageService,
                           DocumentService documentService, MarkdownService markdownService,
                           FileManager fileManager) {
        this.pdfService = pdfService;
        this.pdfCompressService = pdfCompressService;
        this.pdfToImageService = pdfToImageService;
        this.documentService = documentService;
        this.markdownService = markdownService;
        this.fileManager = fileManager;
    }

    // ===== 内部辅助方法: 将 fileId 转为 byte[] =====

    private byte[] loadFile(String fileId) {
        File file = fileManager.load(fileId);
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("无法读取文件: " + fileId, e);
        }
    }

    private String extractFilename(String fileId) {
        // fileId 格式: uuid.ext → 去掉 uuid 保留 ext 作为文件名提示
        return fileId.contains(".") ? "file" + fileId.substring(fileId.lastIndexOf('.')) : "file";
    }

    // ===== @Tool 方法 =====

    @Tool(name = "pdfSplit",
          description = "拆分 PDF 文件。mode: by-page(逐页)/by-range(指定范围)/by-n(每N页)")
    public String pdfSplit(
            @ToolParam("文件 ID") String fileId,
            @ToolParam("拆分模式") String mode,
            @ToolParam(value = "页码范围,mode=by-range 时需要", required = false) String pages,
            @ToolParam(value = "每 N 页,mode=by-n 时需要,默认 1", required = false) Integer everyN) {

        if (mode == null || mode.isBlank()) mode = "by-page";
        if (everyN == null) everyN = 1;

        log.info("[DocAgentToolkit#pdfSplit] mode={}, pages={}, everyN={}", mode, pages, everyN);

        byte[] pdfBytes = loadFile(fileId);
        String filename = extractFilename(fileId);
        byte[] result = pdfService.splitPdf(pdfBytes, filename, mode,
                pages != null ? pages : "", everyN, true);

        String resultId = fileManager.storeBytes(result, filename.replace(".pdf", "_split.zip"),
                "application/zip");
        int pageCount = estimatePageCount(pdfBytes);
        return String.format("切分完成！文件 ID: %s, 大小: %.1fMB",
                resultId, result.length / (1024.0 * 1024.0));
    }

    @Tool(name = "pdfMerge", description = "合并多个 PDF 文件为一个，最多 10 个")
    public String pdfMerge(@ToolParam("文件 ID 列表，逗号分隔") String fileIds) {

        String[] ids = fileIds.split(",");
        if (ids.length < 2) {
            return "错误: 至少需要 2 个 PDF 文件才能合并";
        }
        if (ids.length > 10) {
            return "错误: 最多合并 10 个文件，当前 " + ids.length + " 个";
        }

        log.info("[DocAgentToolkit#pdfMerge] merging {} files", ids.length);

        List<byte[]> bytesList = new java.util.ArrayList<>();
        for (String id : ids) {
            bytesList.add(loadFile(id.trim()));
        }

        byte[] result = pdfService.mergePdf(bytesList, true);
        String resultId = fileManager.storeBytes(result, "merged.pdf", "application/pdf");
        return String.format("合并完成！%d 个文件 → 1 个 PDF, 大小: %.1fMB",
                ids.length, result.length / (1024.0 * 1024.0));
    }

    @Tool(name = "pdfCompress", description = "压缩 PDF 文件, level 1(极度)-5(极限画质)")
    public String pdfCompress(
            @ToolParam("文件 ID") String fileId,
            @ToolParam(value = "压缩等级 1-5, 默认 3", required = false) Integer level) {

        if (level == null) level = 3;
        if (level < 1 || level > 5) {
            return "错误: 压缩等级必须在 1-5 之间";
        }

        log.info("[DocAgentToolkit#pdfCompress] level={}", level);

        byte[] pdfBytes = loadFile(fileId);
        String filename = extractFilename(fileId);
        PdfCompressResult result = pdfCompressService.compress(pdfBytes, filename, level);

        String resultId = fileManager.storeBytes(result.getData(),
                filename.replace(".pdf", "_compressed.pdf"), "application/pdf");
        return String.format("压缩完成！%.1fMB → %.1fMB (%.0f%%), 文件 ID: %s",
                result.getOriginalSize() / (1024.0 * 1024.0),
                result.getCompressedSize() / (1024.0 * 1024.0),
                result.getCompressionRatio() * 100, resultId);
    }

    @Tool(name = "pdfToImage", description = "将 PDF 页面转换为图片")
    public String pdfToImage(
            @ToolParam("文件 ID") String fileId,
            @ToolParam(value = "输出格式: png/jpeg/webp, 默认 png", required = false) String format,
            @ToolParam(value = "DPI 72-600, 默认 150", required = false) Integer dpi,
            @ToolParam(value = "JPEG 质量 0.0-1.0, 默认 0.9", required = false) Float quality,
            @ToolParam(value = "页码范围, 如 '1-5', 不传=全部", required = false) String pageRange) {

        if (format == null || format.isBlank()) format = "png";
        if (dpi == null) dpi = 150;
        if (quality == null) quality = 0.9f;

        log.info("[DocAgentToolkit#pdfToImage] format={}, dpi={}, quality={}", format, dpi, quality);

        byte[] pdfBytes = loadFile(fileId);
        String filename = extractFilename(fileId);
        PdfToImageResult result = pdfToImageService.convertToImages(
                pdfBytes, filename, dpi, format, quality, pageRange);

        String ext = "png".equals(format) ? ".zip" : ("." + format);
        String resultId = fileManager.storeBytes(result.getData(),
                filename.replace(".pdf", "_images" + ext), result.getContentType());
        return String.format("转换完成！格式: %s, DPI: %d, 大小: %.1fMB, 文件 ID: %s",
                format.toUpperCase(), dpi,
                result.getData().length / (1024.0 * 1024.0), resultId);
    }

    @Tool(name = "docToPdf", description = "将 Word/WPS 文档转换为 PDF")
    public String docToPdf(@ToolParam("文件 ID") String fileId) {

        // 检查服务可用性
        if (!documentService.isServiceAvailable()) {
            return "错误: 文档转 PDF 服务暂不可用（LibreOffice 未启动），请联系管理员";
        }

        log.info("[DocAgentToolkit#docToPdf] converting file: {}", fileId);

        byte[] docBytes = loadFile(fileId);
        String filename = extractFilename(fileId);
        byte[] result = documentService.convertToPdf(docBytes, filename);

        String resultId = fileManager.storeBytes(result,
                filename.replaceAll("\\.(doc|docx|wps)$", ".pdf"), "application/pdf");
        return String.format("转换完成！大小: %.1fMB, 文件 ID: %s",
                result.length / (1024.0 * 1024.0), resultId);
    }

    @Tool(name = "mdToDocx", description = "将 Markdown 文本转换为 DOCX 文件")
    public String mdToDocx(
            @ToolParam("Markdown 文本内容") String markdownContent,
            @ToolParam(value = "输出文件名（不含扩展名）, 默认 output", required = false) String outputName) {

        if (outputName == null || outputName.isBlank()) outputName = "output";

        log.info("[DocAgentToolkit#mdToDocx] converting, length={}", markdownContent.length());

        byte[] result = markdownService.convertMarkdownToDocx(markdownContent);
        String resultId = fileManager.storeBytes(result, outputName + ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return String.format("转换完成！%d 字符 → DOCX, 大小: %.1fKB, 文件 ID: %s",
                markdownContent.length(), result.length / 1024.0, resultId);
    }

    // ===== FileManager 需要增加 storeBytes 方法，见下方 =====

    /**
     * 估算 PDF 页数（简单方法: 取 /Type /Page 出现次数）
     */
    private int estimatePageCount(byte[] pdfBytes) {
        // 简化实现 — 搜索 "/Type /Page" 模式
        String content = new String(pdfBytes, 0, Math.min(pdfBytes.length, 1024 * 1024),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf("/Type /Page", idx)) != -1) {
            count++;
            idx += 10;
        }
        return Math.max(count, 1);
    }
}
```

**注意**: `FileManager` 需要新增 `storeBytes` 方法。稍后在 Task 9 中一并添加。

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/toolbox/service/agent/DocAgentToolkit.java
git commit -m "feat(agent): 添加 DocAgentToolkit 6 个 @Tool 方法"
```

---

### Task 9: FileManager 补充 + ErrorClassifier

**Files:**
- Modify: `backend/src/main/java/com/toolbox/service/agent/FileManager.java`
- Create: `backend/src/main/java/com/toolbox/service/agent/ErrorClassifier.java`

- [ ] **Step 1: FileManager 新增 storeBytes 方法**

在 `FileManager.java` 的 `store(MultipartFile)` 方法之后添加:

```java
/**
 * 存储字节数组（处理结果产物）
 *
 * @param data        字节数据
 * @param filename    文件名
 * @param contentType MIME 类型
 * @return fileId
 */
public String storeBytes(byte[] data, String filename, String contentType) {
    String fileId = UUID.randomUUID().toString();
    if (filename.contains(".")) {
        fileId += filename.substring(filename.lastIndexOf('.'));
    }
    Path target = uploadDir.resolve(fileId);
    try {
        Files.write(target, data);
        log.info("[FileManager#storeBytes] result stored: {} ({} bytes)", fileId, data.length);
        return fileId;
    } catch (IOException e) {
        log.error("[FileManager#storeBytes] failed to store result", e);
        throw new RuntimeException("产物存储失败", e);
    }
}
```

- [ ] **Step 2: 实现 ErrorClassifier**

```java
package com.toolbox.service.agent;

import com.toolbox.exception.ErrorCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异常分类处理 — 将原始异常映射为用户友好的 Agent 回复
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class ErrorClassifier {

    private static final Logger log = LoggerFactory.getLogger(ErrorClassifier.class);

    /**
     * 分类异常并返回用户友好的错误消息
     *
     * @param e 原始异常
     * @return 用户可读的错误描述
     */
    public String classify(Throwable e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";

        // 用户层 — 文件相关
        if (msg.contains("文件为空") || msg.contains("empty")) {
            return "文件为空，请上传有效的文件。";
        }
        if (msg.contains("文件过大") || msg.contains("too large")) {
            return "文件超过了大小限制。PDF 切分/压缩/转图片上限 50MB，合并单个上限 5MB。";
        }
        if (msg.contains("文件不存在")) {
            return "文件已过期或不存在，请重新上传。";
        }
        if (msg.contains("加密") || msg.contains("encrypted")) {
            return "该 PDF 有密码保护，请解密后重新上传。";
        }
        if (msg.contains("格式") || msg.contains("format") || msg.contains("不支持")) {
            return "文件格式不支持。支持的格式: PDF/DOC/DOCX/WPS/MD。";
        }

        // 操作层 — 参数/逻辑
        if (msg.contains("至少需要") || msg.contains("too few")) {
            return "文件数量不足。" + msg;
        }
        if (msg.contains("最多") || msg.contains("too many")) {
            return "文件数量超出限制。" + msg;
        }
        if (msg.contains("页") || msg.contains("page")) {
            return "页码范围有误。" + msg;
        }
        if (msg.contains("等级") || msg.contains("level")) {
            return "压缩等级无效，有效范围 1-5。";
        }
        if (msg.contains("不可用") || msg.contains("unavailable")) {
            return "文档转 PDF 服务暂不可用（LibreOffice 未启动），请联系管理员。";
        }
        if (msg.contains("轮次") || msg.contains("上限")) {
            return msg;
        }

        // 系统层 — 兜底
        if (msg.contains("timeout") || msg.contains("超时")) {
            log.error("[ErrorClassifier#classify] LLM timeout");
            return "AI 服务响应超时，请稍后重试。";
        }
        if (msg.contains("disk") || msg.contains("空间")) {
            log.error("[ErrorClassifier#classify] disk full");
            return "服务器存储空间不足，请联系管理员。";
        }

        log.error("[ErrorClassifier#classify] unclassified exception", e);
        return "处理时遇到了问题，请稍后重试。如果持续出现，请联系管理员。";
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/toolbox/service/agent/FileManager.java \
        backend/src/main/java/com/toolbox/service/agent/ErrorClassifier.java
git commit -m "feat(agent): FileManager 新增 storeBytes + 添加 ErrorClassifier 异常分类"
```

---

### Task 10: AgentService + AgentServiceImpl — 编排核心

**Files:**
- Create: `backend/src/main/java/com/toolbox/service/agent/AgentService.java`
- Create: `backend/src/main/java/com/toolbox/service/agent/impl/AgentServiceImpl.java`

**Interfaces:**
- Consumes: ReActAgent、DocAgentToolkit、ConversationManager、FileManager、SseConnectionManager、ErrorClassifier
- Produces: `AgentService.handle(message, files, convId, eventCallback) → void` 供 AgentController 使用

- [ ] **Step 1: 编写 AgentService 接口**

```java
package com.toolbox.service.agent;

import com.toolbox.model.agent.ChatEvent;
import org.springframework.web.multipart.MultipartFile;

import java.util.function.Consumer;

/**
 * Agent 编排服务接口
 *
 * @author toolbox
 * @since 2026-07-15
 */
public interface AgentService {

    /**
     * 处理用户消息并流式推送事件
     *
     * @param message         用户消息文本
     * @param files           上传的文件（可选）
     * @param conversationId  对话 ID（新对话传 null）
     * @param eventConsumer   事件回调，每产生一个 ChatEvent 就调用一次
     * @return 对话 ID
     */
    String handle(String message, MultipartFile[] files, String conversationId,
                  Consumer<ChatEvent> eventConsumer);
}
```

- [ ] **Step 2: 实现 AgentServiceImpl**

```java
package com.toolbox.service.agent.impl;

import com.toolbox.model.agent.ChatEvent;
import com.toolbox.model.agent.ConversationStore.ConversationMessage;
import com.toolbox.service.agent.*;
import io.agentscope.core.agent.ReActAgent;
import io.agentscope.core.msg.Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Agent 编排实现 — 管理对话上下文、运行 ReActAgent、推进 SSE 事件
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class AgentServiceImpl implements AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);

    private final ReActAgent docAgent;
    private final ConversationManager conversationManager;
    private final FileManager fileManager;
    private final ErrorClassifier errorClassifier;

    public AgentServiceImpl(ReActAgent docAgent, ConversationManager conversationManager,
                            FileManager fileManager, ErrorClassifier errorClassifier) {
        this.docAgent = docAgent;
        this.conversationManager = conversationManager;
        this.fileManager = fileManager;
        this.errorClassifier = errorClassifier;
    }

    @Override
    public String handle(String message, MultipartFile[] files, String conversationId,
                         Consumer<ChatEvent> eventConsumer) {

        // 1. 对话管理: 创建或复用对话
        if (conversationId == null || conversationId.isBlank()
                || !conversationManager.exists(conversationId)) {
            conversationId = conversationManager.create();
            log.info("[AgentServiceImpl#handle] new conversation: {}", conversationId);
        }

        // 2. 文件处理: 先存储用户上传的文件
        List<String> fileIds = new ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    try {
                        String fileId = fileManager.store(f);
                        fileIds.add(fileId);
                    } catch (Exception e) {
                        eventConsumer.accept(ChatEvent.error(
                                errorClassifier.classify(e)));
                        return conversationId;
                    }
                }
            }
        }

        // 3. 追加用户消息到对话历史
        conversationManager.appendUserMessage(conversationId, message, fileIds);

        // 4. 构建 Agent 输入（含文件上下文）
        String agentInput = buildAgentInput(message, fileIds);

        // 5. 运行 ReActAgent
        eventConsumer.accept(ChatEvent.thinking("正在分析你的需求..."));
        String finalConvId = conversationId;

        try {
            // AgentScope ReActAgent.run() 返回最终 Msg
            // 中间步骤通过 docAgent 的 onStep 回调推送事件
            // 注意: 具体 API 以 AgentScope 1.0.12 实际为准
            Msg result = docAgent.run(agentInput).block();

            // 6. 提取工具调用结果
            String replyText = extractReply(result);
            eventConsumer.accept(ChatEvent.reply(replyText));

            // 7. 追加助手回复到对话历史
            conversationManager.appendAssistantMessage(finalConvId, replyText);

            // 8. 如果工具产生了产物文件，推送 result 事件
            // （产物信息从 tool 返回的字符串中解析或通过 shared context 传递）
            // 简化实现: reply 中包含文件 ID 则自动追加 result 事件

        } catch (Exception e) {
            log.error("[AgentServiceImpl#handle] agent run failed", e);
            eventConsumer.accept(ChatEvent.error(errorClassifier.classify(e)));
        }

        eventConsumer.accept(ChatEvent.done());
        return conversationId;
    }

    /**
     * 构建包含文件上下文的 Agent 输入
     */
    private String buildAgentInput(String message, List<String> fileIds) {
        if (fileIds.isEmpty()) {
            return message;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户上传了以下文件:\n");
        for (int i = 0; i < fileIds.size(); i++) {
            sb.append("- ").append(fileIds.get(i)).append("\n");
        }
        sb.append("\n用户消息: ").append(message);
        sb.append("\n\n使用 fileId 参数调用工具。");
        return sb.toString();
    }

    /**
     * 从 Agent 返回的 Msg 中提取文字回复
     */
    private String extractReply(Msg msg) {
        if (msg == null) return "处理完成。";
        String text = msg.getTextContent();
        return text != null && !text.isBlank() ? text : "处理完成，请查看结果。";
    }
}
```

> **注意**: AgentScope 1.0.12 的具体 API（`docAgent.run()`、`Msg.getTextContent()` 等）需要在实施时根据实际 jar 包的方法签名调整。核心逻辑（对话管理 + 文件存储 + 运行 Agent + 推送事件）不变。

- [ ] **Step 3: 编译验证并调整 AgentScope API 调用**

```bash
cd backend && mvn compile -q
```

根据编译错误调整 `ReActAgent.run()`、`Msg` 等方法的实际签名。如果 AgentScope 1.0.12 使用不同的 API，查阅其 Javadoc 后修正。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/toolbox/service/agent/AgentService.java \
        backend/src/main/java/com/toolbox/service/agent/impl/AgentServiceImpl.java
git commit -m "feat(agent): 添加 AgentService 编排实现（对话管理 + ReActAgent 运行）"
```

---

## Phase 3: Controller — SSE 流式端点

### Task 11: AgentController — SSE 流式对话端点

**Files:**
- Create: `backend/src/main/java/com/toolbox/controller/AgentController.java`

**Interfaces:**
- Consumes: AgentService、SseConnectionManager、FileManager、ConversationManager
- Produces: `POST /api/agent/chat`（SSE）、`POST /api/agent/cancel`、`GET /api/agent/download/{fileId}`、`GET /api/agent/conversations`、`GET/DELETE /api/agent/conversations/{id}`

- [ ] **Step 1: 实现 AgentController**

```java
package com.toolbox.controller;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.agent.ChatEvent;
import com.toolbox.model.agent.ConversationStore.ConversationEntry;
import com.toolbox.service.agent.AgentService;
import com.toolbox.service.agent.ConversationManager;
import com.toolbox.service.agent.FileManager;
import com.toolbox.service.agent.SseConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agent 对话接口 — SSE 流式返回
 *
 * @author toolbox
 * @since 2026-07-15
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;
    private final SseConnectionManager sseConnectionManager;
    private final FileManager fileManager;
    private final ConversationManager conversationManager;

    public AgentController(AgentService agentService,
                           SseConnectionManager sseConnectionManager,
                           FileManager fileManager,
                           ConversationManager conversationManager) {
        this.agentService = agentService;
        this.sseConnectionManager = sseConnectionManager;
        this.fileManager = fileManager;
        this.conversationManager = conversationManager;
    }

    /**
     * Agent 对话 — SSE 流式返回
     *
     * @param message        用户消息（必填）
     * @param files          上传文件（可选）
     * @param conversationId 对话 ID（可选，新对话不传）
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @RequestParam("message") String message,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "conversationId", required = false) String conversationId) {

        SseEmitter emitter = new SseEmitter(sseConnectionManager.getConnectionTimeoutMs());

        // 注册连接（并发控制 + 单 conversation 互斥）
        String effectiveConvId = conversationId != null ? conversationId : "new-" + System.currentTimeMillis();
        if (!sseConnectionManager.register(effectiveConvId, emitter)) {
            // 连接被拒绝（pool 满或已有活跃连接）
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"text\":\"当前使用人数较多或已有活跃连接，请稍后重试\"}"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        // 启动心跳
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        heartbeat.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat").data("{}"));
            } catch (IOException e) {
                heartbeat.shutdown();
            }
        }, sseConnectionManager.getHeartbeatIntervalMs(),
           sseConnectionManager.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);

        // 异步处理 Agent 对话
        new Thread(() -> {
            try {
                agentService.handle(message, files, conversationId, event -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name(event.getType().name())
                                .data(event));
                    } catch (IOException e) {
                        log.warn("[AgentController#chat] failed to send SSE event", e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("[AgentController#chat] agent processing failed", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(ChatEvent.error("处理时遇到了问题，请稍后重试。")));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                heartbeat.shutdown();
                sseConnectionManager.unregister(effectiveConvId);
            }
        }, "agent-chat-" + effectiveConvId).start();

        return emitter;
    }

    /**
     * 取消当前处理
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@RequestParam("conversationId") String conversationId) {
        sseConnectionManager.unregister(conversationId);
        log.info("[AgentController#cancel] cancelled: {}", conversationId);
        return ResponseEntity.ok().build();
    }

    /**
     * 下载处理结果文件
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId) {
        try {
            File file = fileManager.load(fileId);
            ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(file.toPath()));

            String filename = fileId.contains(".") ? fileId : fileId + ".bin";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            log.warn("[AgentController#download] file not found: {}", fileId);
            throw new BusinessException(ErrorCodeEnum.PDF_FILE_EMPTY.getCode(), "文件不存在或已过期");
        }
    }

    /**
     * 历史对话列表
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationEntry>> listConversations() {
        // 通过 ConversationManager 获取（需要暴露 listActive 方法）
        return ResponseEntity.ok(List.of()); // 简化：一期不实现列表
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<?> deleteConversation(@PathVariable String id) {
        conversationManager.delete(id);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/toolbox/controller/AgentController.java
git commit -m "feat(agent): 添加 AgentController SSE 流式对话端点"
```

---

## Phase 4: Frontend — 对话 UI

### Task 12: useSSE composable

**Files:**
- Create: `frontend/src/composables/useSSE.ts`

- [ ] **Step 1: 实现 useSSE**

```typescript
// frontend/src/composables/useSSE.ts
import { ref, onUnmounted } from 'vue'

export interface SseEvent {
  type: 'thinking' | 'tool_call' | 'progress' | 'result' | 'reply' | 'error' | 'heartbeat' | 'done'
  text?: string
  tool?: string
  params?: string
  fileName?: string
  fileId?: string
  size?: string
  progress?: number
}

export function useSSE() {
  const isConnected = ref(false)
  const lastEvent = ref<SseEvent | null>(null)
  const reconnectCount = ref(0)

  let eventSource: EventSource | null = null
  let heartbeatTimer: ReturnType<typeof setTimeout> | null = null
  const MAX_RECONNECT = 3

  function connect(url: string, onEvent: (event: SseEvent) => void): void {
    disconnect()

    eventSource = new EventSource(url)
    isConnected.value = true

    // 监听各类事件
    const eventTypes = ['thinking', 'tool_call', 'progress', 'result', 'reply', 'error', 'done']
    eventTypes.forEach(type => {
      eventSource!.addEventListener(type, (e: MessageEvent) => {
        const data: SseEvent = JSON.parse(e.data)
        data.type = type as SseEvent['type']
        lastEvent.value = data
        onEvent(data)
        resetHeartbeat()
      })
    })

    // 心跳
    eventSource.addEventListener('heartbeat', () => {
      resetHeartbeat()
    })

    // 连接错误处理
    eventSource.onerror = () => {
      if (reconnectCount.value < MAX_RECONNECT) {
        reconnectCount.value++
        setTimeout(() => connect(url, onEvent), 3000)
      } else {
        isConnected.value = false
        onEvent({ type: 'error', text: '连接已断开，请刷新页面重试' })
      }
    }

    resetHeartbeat()
  }

  function resetHeartbeat(): void {
    if (heartbeatTimer) clearTimeout(heartbeatTimer)
    heartbeatTimer = setTimeout(() => {
      // 30s 无事件，视为断连
      disconnect()
      if (reconnectCount.value < MAX_RECONNECT) {
        reconnectCount.value++
        eventSource = null // 触发重连
      }
    }, 30000)
  }

  function disconnect(): void {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    if (heartbeatTimer) {
      clearTimeout(heartbeatTimer)
      heartbeatTimer = null
    }
    isConnected.value = false
  }

  onUnmounted(() => disconnect())

  return { isConnected, lastEvent, reconnectCount, connect, disconnect }
}
```

- [ ] **Step 2: Commit**

```bash
cd frontend && npm run build  # 验证编译通过
git add frontend/src/composables/useSSE.ts
git commit -m "feat(agent): 添加 useSSE composable"
```

---

### Task 13: useAgentChat composable

**Files:**
- Create: `frontend/src/composables/useAgentChat.ts`

- [ ] **Step 1: 实现 useAgentChat**

```typescript
// frontend/src/composables/useAgentChat.ts
import { ref, nextTick } from 'vue'
import { useSSE, type SseEvent } from './useSSE'

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
  files?: { name: string; size: number }[]
  result?: { fileName: string; fileId: string; size: string }
  isProcessing?: boolean
  isError?: boolean
}

export type ChatState = 'idle' | 'waiting' | 'ready' | 'processing' | 'done' | 'error' | 'cancelled'

export function useAgentChat() {
  const messages = ref<ChatMessage[]>([])
  const state = ref<ChatState>('idle')
  const conversationId = ref<string | null>(null)
  const inputDisabled = ref(false)

  const { connect: sseConnect, disconnect: sseDisconnect } = useSSE()

  // 初始欢迎消息
  function initChat(): void {
    messages.value = [{
      role: 'assistant',
      content: '你好！我是文档处理助手 🤖\n\n可以帮你处理 PDF/Word/WPS/Markdown 文件:\n· PDF 切分 / 合并 / 压缩 / 转图片\n· Word/WPS 文档转 PDF\n· Markdown 转 DOCX\n\n请上传文件或直接告诉我你的需求 👇'
    }]
    state.value = 'idle'
  }

  // 发送消息
  async function sendMessage(text: string, files?: File[]): Promise<void> {
    if (!text.trim() && (!files || files.length === 0)) return
    if (state.value === 'processing') return

    // 添加用户消息
    const userMsg: ChatMessage = {
      role: 'user',
      content: text || '[上传了文件]',
      files: files?.map(f => ({ name: f.name, size: f.size }))
    }
    messages.value.push(userMsg)

    // 添加助手占位消息
    const assistantMsg: ChatMessage = {
      role: 'assistant',
      content: '',
      isProcessing: true
    }
    messages.value.push(assistantMsg)
    state.value = 'processing'
    inputDisabled.value = true

    // 构建 FormData
    const formData = new FormData()
    formData.append('message', text)
    if (files) {
      files.forEach(f => formData.append('files', f))
    }
    if (conversationId.value) {
      formData.append('conversationId', conversationId.value)
    }

    // SSE 连接
    const apiBase = window.location.origin
    // 使用 fetch + ReadableStream 发送 multipart 并接收 SSE
    try {
      const response = await fetch(`${apiBase}/api/agent/chat`, {
        method: 'POST',
        body: formData,
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const reader = response.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('event: ')) {
            const eventType = line.slice(7).trim()
            continue // 等待 data 行
          }
          if (line.startsWith('data: ')) {
            try {
              const data: SseEvent = JSON.parse(line.slice(6))
              handleEvent(data, assistantMsg)
            } catch { /* skip invalid JSON */ }
          }
        }
      }
    } catch (err: any) {
      assistantMsg.content = `连接失败: ${err.message}`
      assistantMsg.isProcessing = false
      assistantMsg.isError = true
      state.value = 'error'
      inputDisabled.value = false
    }

    await nextTick()
  }

  // 处理 SSE 事件
  function handleEvent(event: SseEvent, msg: ChatMessage): void {
    switch (event.type) {
      case 'thinking':
        msg.content = event.text || '思考中...'
        break
      case 'tool_call':
        msg.content = `正在${event.tool === 'pdfSplit' ? '切分' :
          event.tool === 'pdfMerge' ? '合并' :
          event.tool === 'pdfCompress' ? '压缩' :
          event.tool === 'pdfToImage' ? '转换图片' :
          event.tool === 'docToPdf' ? '转 PDF' :
          event.tool === 'mdToDocx' ? '转 DOCX' : '处理'}...`
        break
      case 'reply':
        msg.content = event.text || ''
        msg.isProcessing = false
        state.value = 'done'
        inputDisabled.value = false
        break
      case 'result':
        msg.result = {
          fileName: event.fileName || 'result',
          fileId: event.fileId || '',
          size: event.size || ''
        }
        break
      case 'error':
        msg.content = event.text || '处理出错'
        msg.isProcessing = false
        msg.isError = true
        state.value = 'error'
        inputDisabled.value = false
        break
      case 'done':
        if (msg.isProcessing) {
          msg.isProcessing = false
          state.value = 'done'
          inputDisabled.value = false
        }
        break
    }
  }

  // 取消处理
  async function cancelProcessing(): Promise<void> {
    if (!conversationId.value) return
    sseDisconnect()
    await fetch(`/api/agent/cancel?conversationId=${conversationId.value}`, { method: 'POST' })
    state.value = 'cancelled'
    inputDisabled.value = false
  }

  // 下载结果文件
  function downloadUrl(fileId: string): string {
    return `/api/agent/download/${fileId}`
  }

  return {
    messages, state, conversationId, inputDisabled,
    initChat, sendMessage, cancelProcessing, downloadUrl
  }
}
```

- [ ] **Step 2: Commit**

```bash
cd frontend && npm run build
git add frontend/src/composables/useAgentChat.ts
git commit -m "feat(agent): 添加 useAgentChat composable（状态管理 + SSE 接收）"
```

---

### Task 14: doc-agent/index.vue — 对话 UI 组件

**Files:**
- Create: `frontend/src/tools/doc-agent/index.vue`

- [ ] **Step 1: 实现对话组件**

```vue
<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'doc-agent',
  name: '文档助手',
  description: 'AI 对话式文档处理，支持 PDF 切分/合并/压缩/转图片、文档转 PDF、Markdown 转 DOCX',
  icon: '🤖',
  category: 'file',
}
</script>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { useAgentChat } from '@/composables/useAgentChat'

defineOptions({ inheritAttrs: false })
defineExpose({ meta })

const {
  messages, state, inputDisabled,
  initChat, sendMessage, cancelProcessing, downloadUrl
} = useAgentChat()

const inputText = ref('')
const chatContainer = ref<HTMLElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const pendingFiles = ref<File[]>([])

onMounted(() => initChat())

// 自动滚动到底部
watch(messages, async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}, { deep: true })

// 发送消息
async function handleSend(): void {
  const text = inputText.value.trim()
  const files = [...pendingFiles.value]
  pendingFiles.value = []
  inputText.value = ''
  await sendMessage(text, files.length > 0 ? files : undefined)
}

// 回车发送
function handleKeydown(e: KeyboardEvent): void {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

// 文件选择
function handleFileSelect(e: Event): void {
  const target = e.target as HTMLInputElement
  if (target.files) {
    pendingFiles.value.push(...Array.from(target.files))
  }
  target.value = ''
}

// 拖拽文件
function handleDrop(e: DragEvent): void {
  e.preventDefault()
  if (e.dataTransfer?.files) {
    pendingFiles.value.push(...Array.from(e.dataTransfer.files))
  }
}

// 移除待上传文件
function removeFile(index: number): void {
  pendingFiles.value.splice(index, 1)
}

// 格式化文件大小
function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}
</script>

<template>
  <div class="doc-agent" :style="{
    display: 'flex', flexDirection: 'column', height: 'calc(100vh - 64px)',
    background: 'var(--bg-main)'
  }">
    <!-- 顶部标题栏 -->
    <header :style="{
      padding: '12px 20px', borderBottom: '1px solid var(--border-color)',
      display: 'flex', alignItems: 'center', gap: '8px',
      background: 'var(--bg-surface)'
    }">
      <span style="font-size:20px">🤖</span>
      <span :style="{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }">
        文档助手
      </span>
      <span :style="{ fontSize: '12px', color: 'var(--text-muted)', marginLeft: 'auto' }">
        AI 对话式文档处理
      </span>
    </header>

    <!-- 消息列表 -->
    <div ref="chatContainer" :style="{
      flex: 1, overflowY: 'auto', padding: '16px 20px',
      display: 'flex', flexDirection: 'column', gap: '12px'
    }">
      <div v-for="(msg, i) in messages" :key="i"
        :style="{
          display: 'flex', flexDirection: 'column',
          alignItems: msg.role === 'user' ? 'flex-end' : 'flex-start',
          maxWidth: '100%'
        }">
        <!-- 消息气泡 -->
        <div :style="{
          maxWidth: '75%', padding: '10px 14px', borderRadius: '12px',
          whiteSpace: 'pre-wrap', wordBreak: 'break-word',
          fontSize: '14px', lineHeight: 1.6,
          background: msg.role === 'user'
            ? 'var(--accent-color)'
            : 'var(--bg-surface)',
          color: msg.role === 'user' ? '#fff' : 'var(--text-primary)',
          border: msg.role === 'user' ? 'none' : '1px solid var(--border-color)',
        }">
          <!-- 文件附件 -->
          <div v-if="msg.files" :style="{ marginBottom: '6px' }">
            <div v-for="f in msg.files" :key="f.name" :style="{
              display: 'flex', alignItems: 'center', gap: '6px',
              padding: '4px 8px', borderRadius: '6px',
              background: 'var(--bg-main)', fontSize: '12px'
            }">
              <span>📎</span>
              <span>{{ f.name }}</span>
              <span :style="{ color: 'var(--text-muted)' }">{{ formatSize(f.size) }}</span>
            </div>
          </div>

          <!-- 处理中动画 -->
          <span v-if="msg.isProcessing" style="display:flex;align-items:center;gap:8px">
            <svg width="18" height="18" viewBox="0 0 24 24" style="animation:spin 1s linear infinite">
              <circle cx="12" cy="12" r="10" fill="none" stroke="var(--accent-color)" stroke-width="3"
                stroke-dasharray="32" stroke-linecap="round"/>
            </svg>
            {{ msg.content || '处理中...' }}
          </span>
          <span v-else>{{ msg.content }}</span>

          <!-- 结果卡片 -->
          <div v-if="msg.result" :style="{
            marginTop: '8px', padding: '10px', borderRadius: '8px',
            background: 'var(--bg-main)', border: '1px solid var(--border-color)'
          }">
            <div :style="{ fontSize: '13px', marginBottom: '6px' }">
              📦 {{ msg.result.fileName }} ({{ msg.result.size }})
            </div>
            <a :href="downloadUrl(msg.result.fileId)" :style="{
              padding: '4px 12px', borderRadius: '6px', fontSize: '13px',
              background: 'var(--accent-color)', color: '#fff',
              textDecoration: 'none', display: 'inline-block'
            }">
              ⬇ 下载
            </a>
          </div>
        </div>
      </div>
    </div>

    <!-- 待上传文件预览 -->
    <div v-if="pendingFiles.length > 0" :style="{
      padding: '8px 20px', display: 'flex', gap: '8px', flexWrap: 'wrap',
      borderTop: '1px solid var(--border-color)'
    }">
      <div v-for="(f, idx) in pendingFiles" :key="f.name" :style="{
        display: 'flex', alignItems: 'center', gap: '4px',
        padding: '4px 10px', borderRadius: '16px',
        background: 'var(--bg-surface)', border: '1px solid var(--border-color)',
        fontSize: '12px'
      }">
        <span>📎 {{ f.name }}</span>
        <button @click="removeFile(idx)" :style="{
          background: 'none', border: 'none', cursor: 'pointer',
          color: 'var(--text-muted)', fontSize: '14px', padding: 0
        }">×</button>
      </div>
    </div>

    <!-- 底部输入区 -->
    <div :style="{
      padding: '12px 20px', borderTop: '1px solid var(--border-color)',
      display: 'flex', gap: '8px', alignItems: 'flex-end',
      background: 'var(--bg-surface)'
    }"
      @dragover.prevent
      @drop.prevent="handleDrop">
      <label :style="{
        padding: '8px', borderRadius: '8px', cursor: 'pointer',
        color: 'var(--text-secondary)'
      }">
        <span style="fontSize:20px">📎</span>
        <input ref="fileInput" type="file" multiple hidden
          @change="handleFileSelect"
          accept=".pdf,.doc,.docx,.wps,.md" />
      </label>

      <textarea v-model="inputText" @keydown="handleKeydown"
        :disabled="inputDisabled"
        :placeholder="state === 'processing' ? '处理中...' : '输入你的需求，或直接拖拽文件...'"
        rows="1" :style="{
          flex: 1, padding: '8px 12px', borderRadius: '8px',
          border: '1px solid var(--border-color)',
          background: 'var(--bg-main)', color: 'var(--text-primary)',
          fontSize: '14px', resize: 'none', outline: 'none',
          fontFamily: 'inherit'
        }" />

      <button v-if="state === 'processing'" @click="cancelProcessing" :style="{
        padding: '8px 16px', borderRadius: '8px', border: 'none',
        cursor: 'pointer', fontSize: '14px',
        background: '#e53e3e', color: '#fff'
      }">
        取消
      </button>

      <button v-else @click="handleSend"
        :disabled="!inputText.trim() && pendingFiles.length === 0" :style="{
          padding: '8px 16px', borderRadius: '8px', border: 'none',
          cursor: 'pointer', fontSize: '14px',
          background: 'var(--accent-color)', color: '#fff',
          opacity: (!inputText.trim() && pendingFiles.length === 0) ? 0.5 : 1
        }">
        发送
      </button>
    </div>
  </div>
</template>

<style scoped>
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.doc-agent {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}
textarea:focus {
  border-color: var(--accent-color) !important;
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent-color) 20%, transparent);
}
</style>
```

- [ ] **Step 2: 构建验证**

```bash
cd frontend && npm run build
```

Expected: BUILD SUCCESS，组件自动注册到路由

- [ ] **Step 3: Commit**

```bash
git add frontend/src/tools/doc-agent/index.vue
git commit -m "feat(agent): 添加文档助手对话 UI 组件"
```

---

## Phase 5: Integration — 集成测试 + Docker 验证 + 端到端验收

### Task 15: 后端集成测试

**Files:**
- Create: `backend/src/test/java/com/toolbox/service/agent/DocAgentToolkitTest.java`
- Create: `backend/src/test/java/com/toolbox/service/agent/AgentServiceTest.java`

- [ ] **Step 1: DocAgentToolkit 集成测试**

```java
package com.toolbox.service.agent;

import com.toolbox.service.document.DocumentService;
import com.toolbox.service.markdown.MarkdownService;
import com.toolbox.service.pdf.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocAgentToolkitTest {

    private DocAgentToolkit toolkit;
    private FileManager fileManager;
    private PdfService pdfService;
    private PdfCompressService pdfCompressService;
    private PdfToImageService pdfToImageService;
    private DocumentService documentService;
    private MarkdownService markdownService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        fileManager = new FileManager(tempDir.toString(), 50 * 1024 * 1024,
                java.time.Duration.ofMinutes(30));
        pdfService = mock(PdfService.class);
        pdfCompressService = mock(PdfCompressService.class);
        pdfToImageService = mock(PdfToImageService.class);
        documentService = mock(DocumentService.class);
        markdownService = mock(MarkdownService.class);

        toolkit = new DocAgentToolkit(pdfService, pdfCompressService,
                pdfToImageService, documentService, markdownService, fileManager);
    }

    @Test
    @DisplayName("mdToDocx should convert markdown text to docx")
    void mdToDocx_shouldConvertMarkdown() {
        String md = "# Hello\nThis is **bold**";
        when(markdownService.convertMarkdownToDocx(md)).thenReturn(new byte[]{1, 2, 3});

        String result = toolkit.mdToDocx(md, "test-output");

        assertTrue(result.contains("转换完成"));
        assertTrue(result.contains("test-output"));
    }

    @Test
    @DisplayName("pdfCompress should validate level range")
    void pdfCompress_shouldValidateLevel() {
        // level 0 → invalid
        String result = toolkit.pdfCompress("test.pdf", 0);
        assertTrue(result.contains("压缩等级必须在 1-5 之间"));

        // level 6 → invalid
        result = toolkit.pdfCompress("test.pdf", 6);
        assertTrue(result.contains("压缩等级必须在 1-5 之间"));
    }

    @Test
    @DisplayName("pdfMerge should reject fewer than 2 files")
    void pdfMerge_shouldRejectFewerThanTwo() {
        String result = toolkit.pdfMerge("file1.pdf");
        assertTrue(result.contains("至少需要 2 个"));
    }

    @Test
    @DisplayName("pdfMerge should reject more than 10 files")
    void pdfMerge_shouldRejectMoreThanTen() {
        StringBuilder ids = new StringBuilder();
        for (int i = 1; i <= 11; i++) {
            if (i > 1) ids.append(",");
            ids.append("file").append(i).append(".pdf");
        }
        String result = toolkit.pdfMerge(ids.toString());
        assertTrue(result.contains("最多合并 10 个"));
    }
}
```

- [ ] **Step 2: 运行集成测试**

```bash
cd backend && mvn test -Dtest=DocAgentToolkitTest
```

Expected: 4 tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/toolbox/service/agent/
git commit -m "test(agent): 添加 DocAgentToolkit 集成测试"
```

---

### Task 16: Docker 构建验证 + 端到端验收

- [ ] **Step 1: 定时清理任务注册**

在 `ToolboxApplication.java` 中添加定时清理:

```java
@Bean
public ScheduledExecutorService fileCleanupScheduler(FileManager fileManager) {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.scheduleAtFixedRate(
        fileManager::cleanup, 5, 10, TimeUnit.MINUTES);
    log.info("[ToolboxApplication] file cleanup scheduler started (every 10min)");
    return scheduler;
}
```

- [ ] **Step 2: 全量构建**

```bash
cd frontend && npm run build
cd /Users/xiaoqiang/Desktop/GWQ/project/toolbox/toolbox/backend && mvn clean package -DskipTests
```

Expected: BUILD SUCCESS，jar 包含 prompts 目录

- [ ] **Step 3: 启动验证**

```bash
java -jar target/toolbox-1.0.0.jar &
sleep 10
# 测试 Agent 端点可达（预期 400 因为缺参数）
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8899/api/agent/chat \
  -F "message=测试"
# 预期: 200（SSE 流式）或 400
pkill -f "toolbox-1.0.0.jar"
```

- [ ] **Step 4: Docker 构建验证**

```bash
docker build -t toolbox-lo:1.1.0 .
# 验证镜像大小和层
docker images toolbox-lo:1.1.0
```

Expected: 镜像构建成功，不引入额外系统依赖（AgentScope 是纯 Java 库）

- [ ] **Step 5: 运行全部测试**

```bash
cd backend && mvn test
```

Expected: 所有测试通过（含新增 Agent 测试）

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/toolbox/ToolboxApplication.java
git commit -m "feat(agent): 注册文件清理定时任务 + 全量构建验证通过"
```

---

## 验收检查清单

- [ ] `mvn test` 全部通过（含新增 Agent 测试 ≥ 10 个用例）
- [ ] `npm run build` 前端编译通过，doc-agent 组件自动注册到路由
- [ ] `POST /api/agent/chat` 端点可访问，返回 SSE 流
- [ ] 6 个文档工具均可通过 Agent 对话调用
- [ ] 可选参数引导: 默认优先 + 按需询问
- [ ] 异常场景: 格式错误/文件过大/LLM 超时 → 友好提示
- [ ] SSE 连接: 心跳正常、超时断开、单 conversation 互斥
- [ ] 30 分钟文件自动清理
- [ ] 现有 6 个独立工具页面功能零影响
- [ ] Docker 镜像构建成功
- [ ] `DASHSCOPE_API_KEY` 环境变量注入正常
