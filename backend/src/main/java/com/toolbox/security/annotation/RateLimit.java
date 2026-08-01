package com.toolbox.security.annotation;

import com.toolbox.security.ratelimit.ResourceTier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解 — 声明在 Controller 方法上
 * <p>
 * 优先级：注解显式值 > ResourceTier 默认值
 *
 * @author toolbox
 * @since 2026-07-30
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 每秒生成令牌数
     * <p>
     * 值为 0 表示使用 ResourceTier 的默认值
     */
    double permitsPerSecond() default 0;

    /**
     * 令牌桶容量（允许的最大突发请求数）
     * <p>
     * 值为 0 表示使用 ResourceTier 的默认值
     */
    int burst() default 0;

    /**
     * 资源消耗分级
     */
    ResourceTier tier() default ResourceTier.MEDIUM;
}
