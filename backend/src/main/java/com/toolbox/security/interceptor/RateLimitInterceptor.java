package com.toolbox.security.interceptor;

import com.toolbox.security.annotation.RateLimit;
import com.toolbox.security.ratelimit.RateLimitKeyResolver;
import com.toolbox.security.ratelimit.RateLimitStore;
import com.toolbox.security.ratelimit.ResourceTier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 限流拦截器 — 在请求进入 Controller 前执行令牌桶校验
 * <p>
 * 读取 @RateLimit 注解配置，调用 RateLimitStore 判断是否放行。
 * 被限流时返回 HTTP 429 Too Many Requests。
 *
 * @author toolbox
 * @since 2026-07-30
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    /** HTTP 429 响应体 JSON 模板 */
    private static final String RATE_LIMITED_JSON =
            "{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\"}";

    private final RateLimitStore rateLimitStore;
    private final RateLimitKeyResolver keyResolver;
    private final double defaultPermitsPerSecond;
    private final int defaultBurst;

    /**
     * 构造方法
     *
     * @param rateLimitStore          限流存储
     * @param keyResolver             Key 解析器
     * @param defaultPermitsPerSecond 全局默认每秒令牌数
     * @param defaultBurst            全局默认突发容量
     */
    public RateLimitInterceptor(RateLimitStore rateLimitStore,
                                 RateLimitKeyResolver keyResolver,
                                 double defaultPermitsPerSecond,
                                 int defaultBurst) {
        this.rateLimitStore = rateLimitStore;
        this.keyResolver = keyResolver;
        this.defaultPermitsPerSecond = defaultPermitsPerSecond;
        this.defaultBurst = defaultBurst;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 非 Controller 方法（如静态资源）直接放行
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        // 1. 读取限流配置
        RateLimit limit = hm.getMethodAnnotation(RateLimit.class);
        double permitsPerSecond;
        int burst;

        if (limit != null) {
            ResourceTier tier = limit.tier();
            permitsPerSecond = limit.permitsPerSecond() > 0
                    ? limit.permitsPerSecond() : tier.getDefaultPermitsPerSecond();
            burst = limit.burst() > 0 ? limit.burst() : tier.getDefaultBurst();
        } else {
            // 未标注 @RateLimit 的方法使用全局默认值
            permitsPerSecond = defaultPermitsPerSecond;
            burst = defaultBurst;
        }

        // 2. 构建限流 Key (IP + URI)
        String clientIp = keyResolver.resolve(request);
        String uri = request.getRequestURI();
        String rateLimitKey = clientIp + ":" + uri;

        // 3. 执行令牌桶判断
        if (!rateLimitStore.tryAcquire(rateLimitKey, 1, burst, permitsPerSecond)) {
            log.warn("[RateLimitInterceptor#preHandle] rate limited: key={}, permitsPerSec={}, burst={}",
                    rateLimitKey, permitsPerSecond, burst);

            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("X-RateLimit-Key", rateLimitKey);

            // Retry-After: 估算等待时间（秒）
            long available = rateLimitStore.availableTokens(rateLimitKey, burst, permitsPerSecond);
            long waitSeconds = permitsPerSecond > 0
                    ? Math.max(1, (long) Math.ceil((1 - available) / permitsPerSecond))
                    : 1;
            response.setHeader("Retry-After", String.valueOf(waitSeconds));

            response.getWriter().write(RATE_LIMITED_JSON);
            return false;
        }

        return true;
    }
}
