package com.toolbox.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;

/**
 * Redis 条件装配 — 仅当 store 类型指定 redis 时才激活
 * <p>
 * 否则已排除 RedisAutoConfiguration，应用在无 Redis 环境正常运行。
 *
 * @author toolbox
 * @since 2026-07-17
 */
@Configuration
@ConditionalOnExpression(
    "'${toolbox.store.conversation-store:local}' == 'redis' || " +
    "'${toolbox.store.connection-registry:local}' == 'redis'")
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${toolbox.agent.redis.host:localhost}")
    private String host;

    @Value("${toolbox.agent.redis.port:6379}")
    private int port;

    @Value("${toolbox.agent.redis.password:}")
    private String password;

    @Value("${toolbox.agent.redis.timeout:3000ms}")
    private Duration timeout;

    @Value("${toolbox.agent.redis.lettuce.pool.max-active:8}")
    private int maxActive;

    @Value("${toolbox.agent.redis.lettuce.pool.max-idle:4}")
    private int maxIdle;

    @Value("${toolbox.agent.redis.lettuce.pool.min-idle:2}")
    private int minIdle;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(host);
        serverConfig.setPort(port);
        if (password != null && !password.isBlank()) {
            serverConfig.setPassword(password);
        }

        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(maxActive);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);

        // RESP2 协议 — 兼容 Redis 6.x/7.x/8.x，避免 RESP3 HELLO 认证问题
        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .commandTimeout(timeout)
                .poolConfig(poolConfig)
                .clientOptions(ClientOptions.builder()
                        .protocolVersion(ProtocolVersion.RESP2)
                        .build())
                .build();

        log.info("[RedisConfig] connecting to Redis at {}:{}, timeout={}, pool(max={},idle={},min={})",
                host, port, timeout, maxActive, maxIdle, minIdle);
        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        return template;
    }
}
