package com.toolbox.config;

import com.toolbox.model.agent.ConversationStore;
import com.toolbox.model.agent.impl.InMemoryConversationStore;
import com.toolbox.model.agent.impl.RedisConversationStore;
import com.toolbox.service.agent.ConnectionRegistry;
import com.toolbox.service.agent.EventPublisher;
import com.toolbox.service.agent.impl.LocalConnectionRegistry;
import com.toolbox.service.agent.impl.LocalEventPublisher;
import com.toolbox.service.agent.impl.RedisConnectionRegistry;
import com.toolbox.service.agent.impl.RedisEventPublisher;
import com.toolbox.service.store.FileStore;
import com.toolbox.service.store.impl.LocalFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 存储层配置 — FileStore / ConversationStore / ConnectionRegistry / EventPublisher
 *
 * @author toolbox
 * @since 2026-07-21
 */
@Configuration
public class StoreConfig {

    private static final Logger log = LoggerFactory.getLogger(StoreConfig.class);

    @Value("${toolbox.agent.sse.max-connections:50}")
    private int sseMaxConnections;

    @Value("${toolbox.agent.sse.heartbeat-interval-ms:15000}")
    private long sseHeartbeatMs;

    @Value("${toolbox.agent.sse.connection-timeout-ms:300000}")
    private long sseTimeoutMs;

    @Value("${toolbox.agent.conversation.ttl-minutes:1200}")
    private int conversationTtlMinutes;

    @Value("${toolbox.agent.file.upload-dir:${java.io.tmpdir}/toolbox-agent}")
    private String fileUploadDir;

    @Value("${toolbox.agent.file.max-file-size:52428800}")
    private long fileMaxSize;

    // ===== FileStore =====

    @Bean
    @ConditionalOnProperty(name = "toolbox.store.file-store", havingValue = "local", matchIfMissing = true)
    public FileStore localFileStore() {
        log.info("[StoreConfig] FileStore: local");
        return new LocalFileStore(fileUploadDir, fileMaxSize, Duration.ofMinutes(conversationTtlMinutes));
    }

    // ===== ConversationStore =====

    @Bean
    @ConditionalOnProperty(name = "toolbox.store.conversation-store", havingValue = "local", matchIfMissing = true)
    public ConversationStore localConversationStore() {
        log.info("[StoreConfig] ConversationStore: local");
        return new InMemoryConversationStore();
    }

    @Bean
    @ConditionalOnProperty(name = "toolbox.store.conversation-store", havingValue = "redis")
    public ConversationStore redisConversationStore(StringRedisTemplate redis) {
        log.info("[StoreConfig] ConversationStore: redis");
        return new RedisConversationStore(redis, conversationTtlMinutes);
    }

    // ===== ConnectionRegistry =====

    @Bean
    @ConditionalOnProperty(name = "toolbox.store.connection-registry", havingValue = "local", matchIfMissing = true)
    public ConnectionRegistry localConnectionRegistry() {
        log.info("[StoreConfig] ConnectionRegistry: local");
        return new LocalConnectionRegistry(sseMaxConnections, sseHeartbeatMs, sseTimeoutMs);
    }

    @Bean
    @ConditionalOnProperty(name = "toolbox.store.connection-registry", havingValue = "redis")
    public ConnectionRegistry redisConnectionRegistry(StringRedisTemplate redis) {
        log.info("[StoreConfig] ConnectionRegistry: redis");
        return new RedisConnectionRegistry(sseMaxConnections, sseHeartbeatMs, sseTimeoutMs, redis);
    }

    // ===== EventPublisher =====

    @Bean
    @ConditionalOnProperty(name = "toolbox.store.connection-registry", havingValue = "local", matchIfMissing = true)
    public EventPublisher localEventPublisher(ConnectionRegistry connectionRegistry) {
        log.info("[StoreConfig] EventPublisher: local");
        return new LocalEventPublisher(connectionRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "toolbox.store.connection-registry", havingValue = "redis")
    public EventPublisher redisEventPublisher(ConnectionRegistry connectionRegistry,
                                               StringRedisTemplate redis,
                                               RedisConnectionFactory connectionFactory) {
        log.info("[StoreConfig] EventPublisher: redis");
        return new RedisEventPublisher(redis, connectionRegistry, connectionFactory);
    }
}
