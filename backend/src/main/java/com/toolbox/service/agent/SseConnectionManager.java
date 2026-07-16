package com.toolbox.service.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * SSE 连接池管理 — 并发控制、单 conversation 连接互斥
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

    /** SSE 连接断开回调 — 用于清理对话数据，默认 no-op */
    private Consumer<String> onDisconnect = id -> {};

    public SseConnectionManager(int maxConnections, long heartbeatIntervalMs,
                                 long connectionTimeoutMs) {
        this.maxConnections = maxConnections;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    /**
     * 设置连接断开回调 — 当 SSE 超时/错误/完成时触发，用于清理关联资源
     */
    public void setOnDisconnect(Consumer<String> callback) {
        this.onDisconnect = (callback != null) ? callback : id -> {};
    }

    /**
     * 注册 SSE 连接。单 conversation 互斥，超出全局上限拒绝。
     *
     * @param conversationId 对话 ID
     * @param emitter        SseEmitter
     * @return true 注册成功，false 被拒绝
     */
    public boolean register(String conversationId, SseEmitter emitter) {
        // 全局连接数检查
        if (connections.size() >= maxConnections) {
            log.warn("[SseConnectionManager#register] connection pool full: {} >= {}",
                    connections.size(), maxConnections);
            return false;
        }

        // 单 conversation 互斥
        SseEmitter existing = connections.putIfAbsent(conversationId, emitter);
        if (existing != null) {
            log.info("[SseConnectionManager#register] conversation {} already has active " +
                    "connection, rejecting", conversationId);
            return false;
        }

        // 连接关闭/超时/异常时自动清理 emitter + 关联对话数据
        emitter.onCompletion(() -> {
            connections.remove(conversationId);
            onDisconnect.accept(conversationId);
            log.info("[SseConnectionManager#onCompletion] connection closed: {}", conversationId);
        });
        emitter.onTimeout(() -> {
            connections.remove(conversationId);
            onDisconnect.accept(conversationId);
            log.info("[SseConnectionManager#onTimeout] connection timeout: {}", conversationId);
        });
        emitter.onError(ex -> {
            connections.remove(conversationId);
            onDisconnect.accept(conversationId);
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

    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
}
