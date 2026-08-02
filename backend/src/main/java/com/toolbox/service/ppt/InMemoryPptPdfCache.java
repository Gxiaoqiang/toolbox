package com.toolbox.service.ppt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * JVM 内存实现的 PPT PDF 缓存
 * <p>
 * 基于 ConcurrentHashMap，适合单机部署。
 * 缓存条目带有创建时间戳，定时清理过期条目。
 * <p>
 * 配置：{@code toolbox.ppt.cache-type=memory}（默认）
 *
 * @author toolbox
 * @since 2026-07-22
 */
@Component
@ConditionalOnProperty(name = "toolbox.ppt.cache-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryPptPdfCache implements PptPdfCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryPptPdfCache.class);

    private final ConcurrentHashMap<String, TimedEntry> store = new ConcurrentHashMap<>();

    @Override
    public void put(String cacheKey, byte[] pdfBytes, String baseName) {
        store.put(cacheKey, new TimedEntry(pdfBytes, baseName, System.currentTimeMillis()));
        LOGGER.info("[InMemoryPptPdfCache#put] cached pdf, key={}, size={}KB, totalEntries={}",
                cacheKey, pdfBytes.length / 1024, store.size());
    }

    @Override
    public CachedEntry get(String cacheKey) {
        TimedEntry entry = store.get(cacheKey);
        if (entry == null) {
            return null;
        }
        // 检查是否过期
        if (System.currentTimeMillis() - entry.createdAt > PptConvertConstant.CACHE_TTL_MS) {
            store.remove(cacheKey);
            LOGGER.info("[InMemoryPptPdfCache#get] entry expired, key={}", cacheKey);
            return null;
        }
        return new CachedEntry(entry.pdfBytes, entry.baseName);
    }

    @Override
    public void evict(String cacheKey) {
        store.remove(cacheKey);
    }

    /**
     * 定时清理过期条目
     */
    @Scheduled(fixedRate = PptConvertConstant.CACHE_CLEANUP_INTERVAL_MS)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        int before = store.size();
        store.entrySet().removeIf(e -> now - e.getValue().createdAt > PptConvertConstant.CACHE_TTL_MS);
        int evicted = before - store.size();
        if (evicted > 0) {
            LOGGER.info("[InMemoryPptPdfCache#evictExpired] evicted={}, remaining={}", evicted, store.size());
        }
    }

    private record TimedEntry(byte[] pdfBytes, String baseName, long createdAt) {}
}
