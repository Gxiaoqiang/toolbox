package com.toolbox.service.ppt;

/**
 * PPT 中间 PDF 缓存接口
 * <p>
 * 预览阶段将 LibreOffice 转换后的 PDF 缓存，转换阶段直接复用，
 * 避免重复调用 LibreOffice。
 * <p>
 * 实现方式可选：
 * <ul>
 *   <li>JVM 内存（{@link InMemoryPptPdfCache}）— 开箱即用，重启丢失</li>
 *   <li>Redis（{@link RedisPptPdfCache}）— 分布式共享，适合多实例部署</li>
 * </ul>
 *
 * @author toolbox
 * @since 2026-07-22
 */
public interface PptPdfCache {

    /**
     * 存储中间 PDF
     *
     * @param cacheKey  缓存 key
     * @param pdfBytes  PDF 字节数组
     * @param baseName  原始文件名（不含扩展名）
     */
    void put(String cacheKey, byte[] pdfBytes, String baseName);

    /**
     * 获取缓存的中间 PDF
     *
     * @param cacheKey 缓存 key
     * @return 缓存条目，不存在或已过期返回 null
     */
    CachedEntry get(String cacheKey);

    /**
     * 删除缓存
     *
     * @param cacheKey 缓存 key
     */
    void evict(String cacheKey);

    /**
     * 缓存条目
     */
    record CachedEntry(byte[] pdfBytes, String baseName) {}
}
