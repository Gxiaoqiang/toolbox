package com.toolbox.service.ppt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Base64;

/**
 * Redis 实现的 PPT PDF 缓存
 * <p>
 * 适合多实例部署，缓存在 Redis 中共享。
 * <p>
 * Key 设计: {@code toolbox:ppt-cache:{cacheKey}} → Base64(PDF) + baseName
 * TTL: 10 分钟
 * <p>
 * 配置：{@code toolbox.ppt.cache-type=redis}
 *
 * @author toolbox
 * @since 2026-07-22
 */
@Component
@ConditionalOnProperty(name = "toolbox.ppt.cache-type", havingValue = "redis")
public class RedisPptPdfCache implements PptPdfCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisPptPdfCache.class);

    private static final Duration TTL = Duration.ofMillis(PptConvertConstant.CACHE_TTL_MS);

    private final StringRedisTemplate redis;

    public RedisPptPdfCache(StringRedisTemplate redis) {
        this.redis = redis;
        LOGGER.info("[RedisPptPdfCache#init] initialized, ttlMinutes={}", TTL.toMinutes());
    }

    @Override
    public void put(String cacheKey, byte[] pdfBytes, String baseName) {
        String key = PptConvertConstant.REDIS_KEY_PREFIX + cacheKey;
        // 用 Base64 编码 PDF 字节，存为 "baseName|base64Data" 格式
        String value = baseName + "|" + Base64.getEncoder().encodeToString(pdfBytes);
        redis.opsForValue().set(key, value, TTL);
        LOGGER.info("[RedisPptPdfCache#put] cached pdf, key={}, size={}KB",
                cacheKey, pdfBytes.length / 1024);
    }

    @Override
    public CachedEntry get(String cacheKey) {
        String key = PptConvertConstant.REDIS_KEY_PREFIX + cacheKey;
        String value = redis.opsForValue().get(key);
        if (value == null) {
            return null;
        }

        // 解析 "baseName|base64Data"
        int sep = value.indexOf('|');
        if (sep < 0) {
            LOGGER.warn("[RedisPptPdfCache#get] corrupted cache data, key={}", cacheKey);
            evict(cacheKey);
            return null;
        }

        String baseName = value.substring(0, sep);
        byte[] pdfBytes = Base64.getDecoder().decode(value.substring(sep + 1));
        return new CachedEntry(pdfBytes, baseName);
    }

    @Override
    public void evict(String cacheKey) {
        redis.delete(PptConvertConstant.REDIS_KEY_PREFIX + cacheKey);
    }
}
