package com.toolbox.security.ratelimit;

/**
 * 资源消耗分级枚举
 * <p>
 * 用于标识 API 端点的资源消耗等级，限流策略据此差异化配置。
 *
 * @author toolbox
 * @since 2026-07-30
 */
public enum ResourceTier {

    /** 轻量操作：纯 CPU 计算，无外部进程 */
    LIGHT(5.0, 10),

    /** 中等操作：PDFBox I/O、SSE 长连接 */
    MEDIUM(2.0, 5),

    /** 重量操作：LibreOffice 进程（200-500MB） */
    HEAVY(1.0, 3),

    /** 关键操作：Playwright 浏览器实例（500MB+） */
    CRITICAL(0.5, 2);

    /** 默认每秒生成令牌数 */
    private final double defaultPermitsPerSecond;

    /** 默认桶容量（允许突发） */
    private final int defaultBurst;

    ResourceTier(double defaultPermitsPerSecond, int defaultBurst) {
        this.defaultPermitsPerSecond = defaultPermitsPerSecond;
        this.defaultBurst = defaultBurst;
    }

    public double getDefaultPermitsPerSecond() {
        return defaultPermitsPerSecond;
    }

    public int getDefaultBurst() {
        return defaultBurst;
    }
}
