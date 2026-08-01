package com.toolbox.security.ratelimit.impl;

import com.toolbox.security.ratelimit.RateLimitStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;

/**
 * Redis 分布式限流实现 — 基于 Lua 脚本 + 令牌桶算法
 * <p>
 * Lua 脚本在 Redis 服务端原子执行，保证分布式环境下令牌操作的一致性。
 * Key 设置 TTL 防止僵尸 Key 堆积。
 *
 * @author toolbox
 * @since 2026-07-30
 */
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);

    private static final String KEY_PREFIX = "rate-limit:";

    /**
     * 令牌桶 Lua 脚本
     * <p>
     * KEYS[1] — 令牌桶 Key
     * ARGV[1] — 请求令牌数 (permits)
     * ARGV[2] — 桶容量 (capacity)
     * ARGV[3] — 令牌生成速率 (rate, 每秒)
     * ARGV[4] — 当前时间戳（毫秒）
     * ARGV[5] — Key TTL（秒）
     * <p>
     * 返回: 1=允许, 0=拒绝
     */
    private static final String TOKEN_BUCKET_LUA = """
            local key = KEYS[1]
            local permits = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local rate = tonumber(ARGV[3])
            local now = tonumber(ARGV[4])
            local ttl = tonumber(ARGV[5])

            -- 读取当前桶状态: tokens|lastRefillMs
            local bucket = redis.call('HMGET', key, 'tokens', 'lastRefillMs')
            local tokens = tonumber(bucket[1])
            local lastRefillMs = tonumber(bucket[2])

            -- 首次访问初始化
            if tokens == nil then
                tokens = capacity
                lastRefillMs = now
            end

            -- 计算应补充的令牌数
            local elapsedMs = now - lastRefillMs
            if elapsedMs > 0 then
                local newTokens = (elapsedMs / 1000.0) * rate
                tokens = math.min(capacity, tokens + newTokens)
            end

            -- 判断 + 扣减
            local allowed = 0
            if tokens >= permits then
                tokens = tokens - permits
                allowed = 1
            end

            -- 写回状态
            redis.call('HMSET', key, 'tokens', tokens, 'lastRefillMs', now)
            redis.call('EXPIRE', key, ttl)

            return allowed
            """;

    private final StringRedisTemplate redis;

    private final DefaultRedisScript<Long> tokenBucketScript;

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.tokenBucketScript = new DefaultRedisScript<>(TOKEN_BUCKET_LUA, Long.class);
        log.info("[RedisRateLimitStore#init] initialized with Lua token-bucket script");
    }

    @Override
    public boolean tryAcquire(String key, int permits, int capacity, double rate) {
        String redisKey = KEY_PREFIX + key;
        long now = System.currentTimeMillis();
        long ttl = Math.max(60, (long) (capacity / Math.max(rate, 0.1)) * 2);

        try {
            Long result = redis.execute(
                    tokenBucketScript,
                    List.of(redisKey),
                    String.valueOf(permits),
                    String.valueOf(capacity),
                    String.valueOf(rate),
                    String.valueOf(now),
                    String.valueOf(ttl)
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            // Redis 不可用时降级放行，避免影响业务
            log.error("[RedisRateLimitStore#tryAcquire] redis error, fallback to allow: key={}", key, e);
            return true;
        }
    }

    @Override
    public long availableTokens(String key, int capacity, double rate) {
        String redisKey = KEY_PREFIX + key;
        try {
            List<Object> values = redis.opsForHash()
                    .multiGet(redisKey, List.of("tokens", "lastRefillMs"));
            if (values == null || values.get(0) == null) {
                return capacity;
            }
            double tokens = Double.parseDouble(values.get(0).toString());
            long lastRefillMs = Long.parseLong(values.get(1).toString());

            long elapsedMs = System.currentTimeMillis() - lastRefillMs;
            double newTokens = (elapsedMs / 1000.0) * rate;
            tokens = Math.min(capacity, tokens + newTokens);

            return (long) tokens;
        } catch (Exception e) {
            log.warn("[RedisRateLimitStore#availableTokens] redis error: key={}", key, e);
            return capacity;
        }
    }
}
