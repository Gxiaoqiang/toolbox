package com.toolbox.security.ratelimit.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.toolbox.security.ratelimit.RateLimitStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单机内存限流实现 — 基于 Caffeine Cache + 令牌桶算法
 * <p>
 * 使用 ConcurrentHashMap 存储令牌桶状态，Caffeine Cache 负责冷 Key 自动过期淘汰。
 * ReentrantLock 保证单 Key 下的令牌操作线程安全。
 *
 * @author toolbox
 * @since 2026-07-30
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRateLimitStore.class);

    /** 冷 Key 过期时间：10 分钟无访问则清除 */
    private static final Duration KEY_EXPIRE_DURATION = Duration.ofMinutes(10);

    /** 令牌桶状态缓存（Caffeine 自动过期） */
    private final Cache<String, TokenBucket> buckets;

    /** 每个 Key 的锁，保证令牌操作原子性（同 Key 才竞争） */
    private final ConcurrentHashMap<String, ReentrantLock> locks;

    public InMemoryRateLimitStore() {
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(KEY_EXPIRE_DURATION)
                .build();
        this.locks = new ConcurrentHashMap<>();
        log.info("[InMemoryRateLimitStore#init] initialized with key TTL={}", KEY_EXPIRE_DURATION);
    }

    @Override
    public boolean tryAcquire(String key, int permits, int capacity, double rate) {
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            TokenBucket bucket = buckets.get(key, k -> new TokenBucket(capacity, rate));
            return bucket.tryConsume(permits);
        } finally {
            lock.unlock();
            // 清理长时间不用的锁（避免锁 Map 无限增长）
            if (lock.tryLock()) {
                try {
                    locks.remove(key, lock);
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    public long availableTokens(String key, int capacity, double rate) {
        TokenBucket bucket = buckets.getIfPresent(key);
        if (bucket == null) {
            return capacity;
        }
        bucket.refill();
        return (long) bucket.getTokens();
    }

    /**
     * 令牌桶 — 存储单 Key 的限流状态
     */
    private static class TokenBucket {
        private final int capacity;
        private final double rate;          // 每秒生成令牌数
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(int capacity, double rate) {
            this.capacity = capacity;
            this.rate = rate;
            this.tokens = capacity;         // 初始满桶
            this.lastRefillNanos = System.nanoTime();
        }

        /**
         * 补充令牌（基于时间流逝计算）
         */
        void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            double newTokens = elapsedSeconds * rate;
            if (newTokens > 0) {
                tokens = Math.min(capacity, tokens + newTokens);
                lastRefillNanos = now;
            }
        }

        /**
         * 尝试消费令牌
         *
         * @param permits 需要消费的令牌数
         * @return true=消费成功，false=令牌不足
         */
        boolean tryConsume(int permits) {
            refill();
            if (tokens >= permits) {
                tokens -= permits;
                return true;
            }
            return false;
        }

        double getTokens() {
            return tokens;
        }
    }
}
