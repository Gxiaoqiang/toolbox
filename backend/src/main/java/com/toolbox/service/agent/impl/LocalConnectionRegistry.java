package com.toolbox.service.agent.impl;

import com.toolbox.service.agent.ConnectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 本地内存 SSE 连接注册表 — ConnectionRegistry 默认实现
 *
 * @author toolbox
 * @since 2026-07-16
 */
public class LocalConnectionRegistry implements ConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(LocalConnectionRegistry.class);

    private final int maxConnections;
    private final long heartbeatIntervalMs;
    private final long connectionTimeoutMs;

    /** conversationId → SseEmitter */
    private final ConcurrentHashMap<String, SseEmitter> connections = new ConcurrentHashMap<>();

    /** SSE 连接断开回调 — 用于清理对话数据，默认 no-op */
    private Consumer<String> onDisconnect = id -> {};

    public LocalConnectionRegistry(int maxConnections, long heartbeatIntervalMs,
                                    long connectionTimeoutMs) {
        this.maxConnections = maxConnections;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    @Override
    public void setOnDisconnect(Consumer<String> callback) {
        this.onDisconnect = (callback != null) ? callback : id -> {};
    }

    @Override
    public boolean register(String conversationId, SseEmitter emitter, Runnable cleanup) {
        // 全局连接数检查
        if (connections.size() >= maxConnections) {
            log.warn("[LocalConnectionRegistry#register] connection pool full: {} >= {}",
                    connections.size(), maxConnections);
            return false;
        }

        // 单 conversation 互斥
        SseEmitter existing = connections.putIfAbsent(conversationId, emitter);
        if (existing != null) {
            log.info("[LocalConnectionRegistry#register] conversation {} already has " +
                    "active connection, rejecting", conversationId);
            return false;
        }

        // 连接关闭/超时/异常时自动清理 emitter + 关联对话数据 + 心跳等资源
        emitter.onCompletion(() ->
                doCleanup(conversationId, cleanup, "onCompletion"));
        emitter.onTimeout(() ->
                doCleanup(conversationId, cleanup, "onTimeout"));
        emitter.onError(ex ->
                doCleanup(conversationId, cleanup, "onError"));

        // NOTE: emitter 创建后立即注册，之间不存在异步 gap，完成事件不会在此丢失

        log.info("[LocalConnectionRegistry#register] registered: {} (active: {})",
                conversationId, connections.size());
        return true;
    }

    @Override
    public SseEmitter getEmitter(String conversationId) {
        return connections.get(conversationId);
    }

    private void doCleanup(String conversationId, Runnable cleanup, String reason) {
        connections.remove(conversationId);
        onDisconnect.accept(conversationId);
        if (cleanup != null) cleanup.run();
        log.info("[LocalConnectionRegistry#{}] connection cleanup: {}", reason, conversationId);
    }

    @Override
    public void unregister(String conversationId) {
        SseEmitter emitter = connections.remove(conversationId);
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
}
