package com.toolbox.service.agent.impl;

import com.toolbox.model.agent.ChatEvent;
import com.toolbox.service.agent.ConnectionRegistry;
import com.toolbox.service.agent.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 本地 SSE 事件发布 — 直接从本机 ConnectionRegistry 查找 emitter 发送
 *
 * @author toolbox
 * @since 2026-07-17
 */
public class LocalEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalEventPublisher.class);

    private final ConnectionRegistry connectionRegistry;

    public LocalEventPublisher(ConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public void publish(String conversationId, ChatEvent event) {
        SseEmitter emitter = connectionRegistry.getEmitter(conversationId);
        if (emitter == null) {
            log.debug("[LocalEventPublisher#publish] no local emitter for {}", conversationId);
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getType())
                    .data(event.toJson()));
        } catch (IOException e) {
            log.warn("[LocalEventPublisher#publish] send failed for {}", conversationId, e);
        }
    }
}
