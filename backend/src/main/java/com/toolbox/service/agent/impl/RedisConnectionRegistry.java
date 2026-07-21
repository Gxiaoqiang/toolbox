package com.toolbox.service.agent.impl;

import com.toolbox.service.agent.ConnectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Redis 感知的 SSE 连接注册表 — ConnectionRegistry 的分布式实现
 *
 * <p>在本地 ConcurrentHashMap 基础上，通过 Redis SETNX 实现跨实例的
 * conversation 互斥锁，并借助 Lua 脚本保证锁释放的原子性。
 *
 * <pre>
 * Key 设计:
 *   toolbox:connections:{convId}  → String  {instanceId}  (EXPIRE = timeout)
 * </pre>
 *
 * @author toolbox
 * @since 2026-07-17
 */
public class RedisConnectionRegistry implements ConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(RedisConnectionRegistry.class);

    private static final String KEY_PREFIX = "toolbox:connections:";

    /**
     * Lua 脚本：原子性校验并释放锁 — 仅当 value 匹配当前 instanceId 时才 DEL
     * <pre>
     * KEYS[1] = 锁 key
     * ARGV[1] = 期望的 instanceId
     * 返回: 1 = 成功释放, 0 = 锁不属于当前实例(已过期被其他实例抢占)
     * </pre>
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('DEL', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end");
        script.setResultType(Long.class);
        UNLOCK_SCRIPT = script;
    }

    private final int maxConnections;
    private final long heartbeatIntervalMs;
    private final long connectionTimeoutMs;

    /** conversationId → SseEmitter（本机） */
    private final ConcurrentHashMap<String, SseEmitter> connections = new ConcurrentHashMap<>();

    /** SSE 连接断开回调 */
    private Consumer<String> onDisconnect = id -> {};

    /** conversationId → processing flag（Agent 执行中标记） */
    private final ConcurrentHashMap<String, Boolean> processingSet = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;
    private final String instanceId;

    public RedisConnectionRegistry(int maxConnections, long heartbeatIntervalMs,
                                    long connectionTimeoutMs,
                                    StringRedisTemplate redis) {
        this.maxConnections = maxConnections;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.redis = redis;
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[RedisConnectionRegistry] initialized, instanceId={}", instanceId);
    }

    @Override
    public void setOnDisconnect(Consumer<String> callback) {
        this.onDisconnect = (callback != null) ? callback : id -> {};
    }

    @Override
    public boolean register(String conversationId, SseEmitter emitter, Runnable cleanup) {
        // 全局连接数检查
        if (connections.size() >= maxConnections) {
            log.warn("[RedisConnectionRegistry#register] connection pool full: {} >= {}",
                    connections.size(), maxConnections);
            return false;
        }

        String lockKey = KEY_PREFIX + conversationId;

        // 单 conversation 互斥：Redis SETNX 原子抢锁
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(lockKey, instanceId,
                        connectionTimeoutMs, TimeUnit.MILLISECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            String owner = redis.opsForValue().get(lockKey);
            log.info("[RedisConnectionRegistry#register] conversation {} already owned " +
                    "by instance {}, rejecting", conversationId, owner);
            return false;
        }

        SseEmitter existing = connections.putIfAbsent(conversationId, emitter);
        if (existing != null) {
            // 极端情况：本地已有，Lua 原子释放 Redis 锁
            safeUnlock(lockKey);
            log.info("[RedisConnectionRegistry#register] conversation {} already has " +
                    "local connection, rejecting", conversationId);
            return false;
        }

        // 连接关闭/超时/异常时自动清理（Lua 原子释放锁）
        emitter.onCompletion(() -> doCleanup(conversationId, cleanup, "onCompletion"));
        emitter.onTimeout(() -> doCleanup(conversationId, cleanup, "onTimeout"));
        emitter.onError(ex -> doCleanup(conversationId, cleanup, "onError"));

        // NOTE: emitter 创建后立即注册，之间不存在异步 gap

        log.info("[RedisConnectionRegistry#register] registered: {} (active: {}, " +
                "instance: {})", conversationId, connections.size(), instanceId);
        return true;
    }

    @Override
    public void unregister(String conversationId) {
        SseEmitter emitter = connections.remove(conversationId);
        // Lua 原子释放：仅当锁仍属于本实例时才删除
        safeUnlock(KEY_PREFIX + conversationId);
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    @Override
    public int getActiveCount() { return connections.size(); }

    @Override
    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }

    @Override
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }

    @Override
    public SseEmitter getEmitter(String conversationId) {
        return connections.get(conversationId);
    }

    /** 本机实例 ID（用于日志/监控） */
    public String getInstanceId() { return instanceId; }

    private void doCleanup(String conversationId, Runnable cleanup, String reason) {
        connections.remove(conversationId);
        // Lua 原子释放：仅当锁仍属于本实例时才删除
        safeUnlock(KEY_PREFIX + conversationId);
        onDisconnect.accept(conversationId);
        if (cleanup != null) cleanup.run();
        log.info("[RedisConnectionRegistry#{}] connection cleanup: {}", reason,
                conversationId);
    }

    /**
     * Lua 原子释放锁 — 仅当 Redis 中的值匹配当前 instanceId 时才删除。
     * <p>
     * 防止场景：锁因超时自动过期后被其他实例抢占，此时本实例不应误删他人的锁。
     */
    private void safeUnlock(String lockKey) {
        try {
            Long result = redis.execute(
                    UNLOCK_SCRIPT,
                    Collections.singletonList(lockKey),
                    instanceId);
            if (result != null && result == 0) {
                log.debug("[RedisConnectionRegistry#safeUnlock] lock already owned " +
                        "by another instance, skipped: {}", lockKey);
            }
        } catch (Exception e) {
            log.warn("[RedisConnectionRegistry#safeUnlock] unlock failed for {}: {}",
                    lockKey, e.getMessage());
        }
    }

    @Override
    public void setProcessing(String conversationId) {
        processingSet.put(conversationId, Boolean.TRUE);
    }

    @Override
    public void clearProcessing(String conversationId) {
        processingSet.remove(conversationId);
    }

    @Override
    public boolean isProcessing(String conversationId) {
        return processingSet.containsKey(conversationId);
    }
}
