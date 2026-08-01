package com.toolbox.security.ratelimit;

/**
 * 限流 Key 解析器 — 函数式接口
 * <p>
 * 将请求上下文解析为限流键字符串。
 * Servlet 环境传入 HttpServletRequest，SCG 环境传入 ServerWebExchange。
 *
 * @author toolbox
 * @since 2026-07-30
 */
@FunctionalInterface
public interface RateLimitKeyResolver {

    /**
     * 从请求上下文中提取限流标识（如客户端 IP）
     *
     * @param context 请求上下文（HttpServletRequest / ServerWebExchange）
     * @return 限流标识字符串
     */
    String resolve(Object context);
}
