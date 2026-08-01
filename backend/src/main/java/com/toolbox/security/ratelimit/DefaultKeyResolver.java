package com.toolbox.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认限流 Key 解析器 — 基于客户端 IP + 请求路径
 * <p>
 * 支持 X-Forwarded-For 代理头（取第一个非内网 IP）。
 *
 * @author toolbox
 * @since 2026-07-30
 */
public class DefaultKeyResolver implements RateLimitKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultKeyResolver.class);

    @Override
    public String resolve(Object context) {
        if (context instanceof HttpServletRequest request) {
            return extractClientIp(request);
        }
        log.warn("[DefaultKeyResolver#resolve] unsupported context type: {}",
                context != null ? context.getClass().getName() : "null");
        return "unknown";
    }

    /**
     * 提取请求路径（不含查询参数）
     *
     * @param request HTTP 请求
     * @return 请求路径，如 "/api/pdf/compress"
     */
    public String resolvePath(Object context) {
        if (context instanceof HttpServletRequest request) {
            return request.getRequestURI();
        }
        return "/unknown";
    }

    /**
     * 提取客户端真实 IP
     * <p>
     * 优先级：X-Forwarded-For（第一个非内网 IP）→ X-Real-IP → RemoteAddr
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    private String extractClientIp(HttpServletRequest request) {
        // 1. 尝试从 X-Forwarded-For 取第一个非内网 IP
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] ips = forwarded.split(",");
            for (String ip : ips) {
                String trimmed = ip.trim();
                if (!trimmed.isEmpty() && !isPrivateIp(trimmed)) {
                    return trimmed;
                }
            }
            // 如果全是内网 IP，取第一个
            String first = ips[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }

        // 2. 尝试 X-Real-IP
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        // 3. 兜底取 RemoteAddr
        return request.getRemoteAddr();
    }

    /**
     * 判断是否为内网 IP
     */
    private boolean isPrivateIp(String ip) {
        // 简化判断：以 10./172.16-31./192.168./127. 开头
        return ip.startsWith("10.")
                || ip.startsWith("172.") && isPrivate172(ip)
                || ip.startsWith("192.168.")
                || ip.startsWith("127.")
                || ip.equals("0:0:0:0:0:0:0:1")
                || ip.equals("::1");
    }

    /**
     * 判断 172.x 是否为私有段（172.16.0.0 - 172.31.255.255）
     */
    private boolean isPrivate172(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length < 2) return false;
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
