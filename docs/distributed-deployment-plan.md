# Toolbox 分布式部署改造方案

> 2026-07-16 | 基于架构评审 + grilling 会话结论

## 当前状态

单机 Spring Boot JAR，以下组件依赖本地资源：

| 组件 | 单机实现 | 分布式瓶颈 |
|------|---------|-----------|
| FileStore | `FileManager` 读写本地磁盘 `/tmp` | 实例间文件不共享 |
| ConversationStore | `InMemoryConversationStore` (ConcurrentHashMap) | 内存不共享，重连到其他实例丢失对话 |
| SSE 连接 | `SseConnectionManager` 本地 emitter Map | SSE 绑定单个 JVM |
| 文档转换 | `DocumentServiceImpl` 本地 fork soffice | 本地 Semaphore 管不了全局并发 |
| 工具产物 | `DocAgentToolkit.volatile lastResult` | 单例共享字段，多对话串值 |
| 定时清理 | `@Scheduled` 扫描本地磁盘 | OSS 不需要应用层清理 |
| 密钥配置 | `application.yml` 明文 API Key | 安全风险，多环境不一致 |

---

## 改造方案

### 1. FileStore — 可插拔存储抽象

新增 `FileStore` 接口，默认 `LocalFileStore` 实现，后续加 `OssFileStore`。

```java
public interface FileStore {
    String store(byte[] data, String filename);
    String store(MultipartFile file);
    byte[] load(String fileId);
    void delete(String fileId);
}
```

| 实现 | 适用场景 | 清理策略 |
|------|---------|---------|
| `LocalFileStore` | 单机开发 / 共享 NAS | `@Scheduled` 定时扫磁盘 |
| `OssFileStore` | 分布式生产 | OSS Bucket 生命周期规则自动过期 |
| `JdbcFileStore` | 无 OSS 环境 | 定时 DELETE WHERE expired_at < NOW() |

**改动范围**: `FileManager` → 实现 `FileStore` 接口，`DocAgentToolkit` / `AgentController` 依赖接口。

---

### 2. ConversationStore — 共享会话存储

接口已定义（`ConversationStore.java`），新增两种实现：

```java
// Redis 方案 — 推荐
public class RedisConversationStore implements ConversationStore {
    // Key: toolbox:conv:{id}       → Hash {title, roundCount, createdAt}
    // Key: toolbox:conv:{id}:msgs  → List [JSON messages]
    // TTL: 30 分钟自动过期
}

// JDBC 方案 — 无 Redis 时使用
public class JdbcConversationStore implements ConversationStore {
    // 表: conversations(id, title, round_count, created_at, updated_at)
    // 表: conversation_messages(id, conv_id, role, content, created_at)
}
```

**改动范围**: `DocAgentConfig` 根据配置选择实现，其余代码不变（已依赖接口）。

---

### 3. SSE — Redis Pub/Sub 广播

核心改造：网关无需粘性会话，消息通过 Redis 跨实例投递。

```
用户 A ──SSE──> 网关(haproxy) ──> 实例 1 (持有 SSE + subscribe Redis)
用户 B ──SSE──> 网关(haproxy) ──> 实例 2 (持有 SSE + subscribe Redis)
                                       │
                                  Redis Pub/Sub
                                  channel: toolbox:sse:{convId}
                                       │
             Agent 处理完 ──> publish(event) ──> 所有实例收到
                                       │
             持有该 convId SSE 的实例 ──> 推送，其余忽略
```

**组件拆分**:

```java
// 拆成两个职责
public class SseConnectionManager {
    // 不变：本机 ConcurrentHashMap<convId, SseEmitter>
    // 不变：连接注册/注销/互斥
}

public class RedisSseBroadcaster {
    // 新增：启动时 subscribe("toolbox:sse:*")
    // 收到消息 -> 查 SseConnectionManager 本地有无该 emitter -> 有则推送
    // Agent 处理完 -> redisTemplate.convertAndSend("toolbox:sse:" + convId, event)
}
```

**网关配置（Nginx 示例）**:

```nginx
upstream toolbox {
    server 10.212.16.1:8898;
    server 10.212.16.2:8898;
    server 10.212.16.3:8898;
}

location /api/agent/chat {
    proxy_pass http://toolbox;
    proxy_buffering off;           # 关键！SSE 必须关闭缓冲
    proxy_read_timeout 7200s;      # 与 SSE 超时一致
}
```

**扩容/缩容**: 网关不需要改配置，新实例启动后 subscribe Redis 即生效。实例下线 -> 客户端 EventSource 自动重连 -> 打到其他实例 -> Redis Streams 补发错过消息。

---

### 4. DocumentService — 统一镜像

每台实例都装 LibreOffice，镜像统一。当前 Docker 镜像已内置，不需要拆分轻/重池。

- 镜像: `toolbox-base:1.0` (LibreOffice + 中文字体)
- 并发控制: 本地 `Semaphore` 不变，每台独立限制
- 如果后续需要全局并发限制：改用 Redis 计数器

**Nginx 不需要按路径分流** — 所有请求 round-robin 即可。

---

### 5. DocAgentToolkit — 消除共享可变状态

**当前反模式**:

```java
// 单例 bean，所有对话共享
private volatile ToolResult lastResult;  // 并发串值

// Agent 跑完后来"取"
toolkit.getLastResult();  // 取完就清，双读返回 null
```

**改为双通道模式**（业内标准）:

```java
@Tool
public ToolResponse compressPdf(...) {
    byte[] result = pdfCompressService.compress(...);
    return ToolResponse.of(
        TextBlock.text("压缩完成，文件 ID: " + fileId),       // LLM 通道
        FileBlock.of(fileId, fileName, result.length)         // 产物通道
    );
}

// AgentServiceImpl 从消息中提取，不走旁路
Msg result = docAgent.call(messages, context);
FileBlock file = result.getContentBlocks("file").get(0);
```

**改动范围**: `DocAgentToolkit` 的 6 个 `@Tool` 方法，`AgentServiceImpl` 的结果提取逻辑。移除 `lastResult` 字段和 `getLastResult()` 方法。

---

### 6. 定时任务 — 清理逻辑跟随实现

| 实现 | 清理方式 |
|------|---------|
| `LocalFileStore` | 实现内部 `@Scheduled` 扫本地 `/tmp` |
| `OssFileStore` | 不清理 — OSS Bucket 配生命周期规则 |
| `DocumentServiceImpl.cleanupOrphanedDirs` | 每实例独立清理本机，不变 |

清理逻辑作为 `FileStore` 的实现细节，不在接口层暴露。

---

### 7. 配置外提 — 环境变量

```yaml
# application.yml
server:
  port: ${SERVER_PORT:8898}

toolbox:
  agent:
    llm-api-key: ${LLM_API_KEY}          # 必填，启动即校验（fail-fast）
    llm-base-url: ${LLM_BASE_URL:}        # 可选
```

**部署时通过 `.env` 文件管理**:

```bash
# /opt/toolbox/.env (chmod 600)
LLM_API_KEY=sk-your-key
LLM_BASE_URL=http://your-proxy:8080/v1
SERVER_PORT=8898
```

`deploy-server.sh` 启动时自动 source 该文件并注入容器。

---

## 改造优先级

```
Phase 1 (现在可做 — 接口抽象 + 本地实现)          Phase 2 (Redis 就绪后)           Phase 3 (弹性伸缩)
+--------------------------------------+     +----------------------+     +------------------+
| 1. FileStore 接口 + LocalFileStore   |     | FileStore -> OssImpl  |     | 多实例 docker run |
| 2. EventPublisher 接口 + LocalImpl   |     | ConvStore -> RedisImpl|     | 网关 round-robin  |
| 3. ConversationStore 已有接口/实现    |     | EventPublisher->Redis|     |                  |
| 4. Toolkit 双通道（消除 lastResult）  |     |                      |     |                  |
| 5. 定时任务归入 LocalFileStore       |     |                      |     |                  |
| 6. 配置外提 已完成                   |     |                      |     |                  |
+--------------------------------------+     +----------------------+     +------------------+
```

Phase 1 全是接口抽象 + 本地实现，不依赖任何外部基础设施。代码结构先对齐分布式架构，Redis/OSS 就绪后只加 impl 即可。
