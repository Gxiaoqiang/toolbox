package com.toolbox.security.config;

import com.toolbox.security.interceptor.RateLimitInterceptor;
import com.toolbox.security.ratelimit.DefaultKeyResolver;
import com.toolbox.security.ratelimit.RateLimitKeyResolver;
import com.toolbox.security.ratelimit.RateLimitStore;
import com.toolbox.security.ratelimit.impl.InMemoryRateLimitStore;
import com.toolbox.security.ratelimit.impl.RedisRateLimitStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 安全防控配置 — 限流存储装配 + 拦截器 Bean 定义
 * <p>
 * 遵循 StoreConfig 的 @ConditionalOnProperty 装配模式，
 * 支持 local（单机内存）和 redis（分布式）两种限流后端。
 * 当 toolbox.security.rate-limit.enabled=false 时，整个限流模块不装配。
 *
 * @author toolbox
 * @since 2026-07-30
 */
@Configuration
@ConditionalOnProperty(name = "toolbox.security.rate-limit.enabled",
        havingValue = "true", matchIfMissing = true)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${toolbox.security.rate-limit.default-permits-per-second:5.0}")
    private double defaultPermitsPerSecond;

    @Value("${toolbox.security.rate-limit.default-burst:10}")
    private int defaultBurst;

    // ===== RateLimitStore =====

    /**
     * 单机内存限流存储（默认）
     */
    @Bean
    @ConditionalOnProperty(name = "toolbox.security.rate-limit.store",
            havingValue = "local", matchIfMissing = true)
    public RateLimitStore localRateLimitStore() {
        log.info("[SecurityConfig] RateLimitStore: local (Caffeine + token-bucket)");
        return new InMemoryRateLimitStore();
    }

    /**
     * Redis 分布式限流存储
     */
    @Bean
    @ConditionalOnProperty(name = "toolbox.security.rate-limit.store", havingValue = "redis")
    public RateLimitStore redisRateLimitStore(StringRedisTemplate redis) {
        log.info("[SecurityConfig] RateLimitStore: redis (Lua + token-bucket)");
        return new RedisRateLimitStore(redis);
    }

    // ===== RateLimitKeyResolver =====

    /**
     * 默认 Key 解析器（基于客户端 IP + 请求路径）
     */
    @Bean
    public RateLimitKeyResolver rateLimitKeyResolver() {
        return new DefaultKeyResolver();
    }

    // ===== RateLimitInterceptor =====

    /**
     * 限流拦截器
     */
    @Bean
    public RateLimitInterceptor rateLimitInterceptor(RateLimitStore rateLimitStore,
                                                      RateLimitKeyResolver rateLimitKeyResolver) {
        return new RateLimitInterceptor(rateLimitStore, rateLimitKeyResolver,
                defaultPermitsPerSecond, defaultBurst);
    }
}
