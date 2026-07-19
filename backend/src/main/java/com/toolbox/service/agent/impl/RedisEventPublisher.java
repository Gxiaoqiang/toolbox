package com.toolbox.service.agent.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolbox.model.agent.ChatEvent;
import com.toolbox.service.agent.ConnectionRegistry;
import com.toolbox.service.agent.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Redis Pub/Sub SSE 事件发布 — 跨实例事件投递
 *
 * <pre>
 * 流程:
 *   1. Agent 线程调用 publish(convId, event)
 *   2. 信封包装 → redis.convertAndSend("toolbox:events:{convId}", envelopeJson)
 *   3. 所有实例的 subscriber（订阅 pattern "toolbox:events:*"）收到消息
 *   4. 解析信封，提取 conversationId，查找本机 ConnectionRegistry.getEmitter(convId)
 *   5. 命中 → emitter.send(...)  未命中 → 忽略（连接在其他实例）
 * </pre>
 *
 * @author toolbox
 * @since 2026-07-17
 */
public class RedisEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisEventPublisher.class);

    private static final String CHANNEL_PREFIX = "toolbox:events:";
    private static final String CHANNEL_PATTERN = "toolbox:events:*";

    /**
     * Pub/Sub 信封 — conversationId 用于路由，ChatEvent 是实际载荷
     */
    private record EventEnvelope(
            @JsonProperty("conversationId") String conversationId,
            @JsonProperty("event") ChatEvent event) {}

    private final StringRedisTemplate redis;
    private final ConnectionRegistry connectionRegistry;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer listenerContainer;

    public RedisEventPublisher(StringRedisTemplate redis,
                                ConnectionRegistry connectionRegistry,
                                RedisConnectionFactory connectionFactory) {
        this.redis = redis;
        this.connectionRegistry = connectionRegistry;
        this.objectMapper = new ObjectMapper();

        // 启动 Redis 消息监听器 — 订阅所有 toolbox:events:* 通道
        this.listenerContainer = new RedisMessageListenerContainer();
        this.listenerContainer.setConnectionFactory(connectionFactory);
        this.listenerContainer.addMessageListener(
                (message, pattern) -> onMessage(message.getBody()),
                new PatternTopic(CHANNEL_PATTERN));
        this.listenerContainer.afterPropertiesSet();
        this.listenerContainer.start();

        log.info("[RedisEventPublisher] subscribed to pattern: {}", CHANNEL_PATTERN);
    }

    @Override
    public void publish(String conversationId, ChatEvent event) {
        try {
            EventEnvelope envelope = new EventEnvelope(conversationId, event);
            String json = objectMapper.writeValueAsString(envelope);
            redis.convertAndSend(CHANNEL_PREFIX + conversationId, json);
        } catch (IOException e) {
            log.error("[RedisEventPublisher#publish] failed to serialize for {}",
                    conversationId, e);
        }
    }

    /**
     * 收到 Redis Pub/Sub 消息 — 查找本机 emitter 并投递
     */
    private void onMessage(byte[] body) {
        try {
            EventEnvelope envelope = objectMapper.readValue(body, EventEnvelope.class);
            ChatEvent event = envelope.event();
            String conversationId = envelope.conversationId();

            SseEmitter emitter = connectionRegistry.getEmitter(conversationId);
            if (emitter != null) {
                emitter.send(SseEmitter.event()
                        .name(event.getType())
                        .data(event.toJson()));
            }
            // else: 连接在其他实例，忽略
        } catch (Exception e) {
            log.warn("[RedisEventPublisher#onMessage] failed to process message", e);
        }
    }
}
