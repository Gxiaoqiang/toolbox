package com.toolbox.security.ratelimit;

/**
 * 限流存储抽象接口 — 支持单机内存 / Redis 两种后端
 * <p>
 * 接口设计不依赖任何 Servlet API，预留 Spring Cloud Gateway 集成能力。
 *
 * @author toolbox
 * @since 2026-07-30
 */
public interface RateLimitStore {

    /**
     * 尝试获取令牌（令牌桶算法）
     *
     * @param key      限流键（如 "rate-limit:192.168.1.1:/api/pdf/compress"）
     * @param permits  本次请求消耗的令牌数
     * @param capacity 令牌桶容量（最大突发）
     * @param rate     令牌生成速率（每秒）
     * @return true=允许通过，false=触发限流
     */
    boolean tryAcquire(String key, int permits, int capacity, double rate);

    /**
     * 查询当前剩余令牌数
     * <p>
     * 用于生成 Retry-After 响应头，告知客户端大约需要等待多久。
     *
     * @param key      限流键
     * @param capacity 令牌桶容量
     * @param rate     令牌生成速率
     * @return 当前剩余令牌数（0 表示完全耗尽）
     */
    long availableTokens(String key, int capacity, double rate);
}
