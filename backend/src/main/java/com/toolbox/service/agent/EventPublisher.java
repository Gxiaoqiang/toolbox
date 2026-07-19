package com.toolbox.service.agent;

import com.toolbox.model.agent.ChatEvent;

/**
 * SSE 事件发布抽象 — 可插拔实现：本地直投 / Redis Pub/Sub 跨实例广播
 *
 * @author toolbox
 * @since 2026-07-17
 */
public interface EventPublisher {

    /**
     * 发布 SSE 事件到指定对话的客户端。
     * 本地实现：直接查找本机 emitter 发送。
     * Redis 实现：publish 到 Redis channel，持有连接的实例负责投递。
     *
     * @param conversationId 对话 ID
     * @param event          ChatEvent
     */
    void publish(String conversationId, ChatEvent event);
}
