package com.toolbox.config;

import com.toolbox.security.interceptor.RateLimitInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * @author toolbox
 * @since 2026-07-01
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<RateLimitInterceptor> rateLimitInterceptorProvider;

    public WebMvcConfig(ObjectProvider<RateLimitInterceptor> rateLimitInterceptorProvider) {
        this.rateLimitInterceptorProvider = rateLimitInterceptorProvider;
    }

    /**
     * 注册限流拦截器 — 对所有 /api/** 请求生效
     * <p>
     * 使用 ObjectProvider 注入，当限流功能未启用（如测试环境）时自动跳过。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        RateLimitInterceptor interceptor = rateLimitInterceptorProvider.getIfAvailable();
        if (interceptor != null) {
            registry.addInterceptor(interceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns("/api/agent/download/**");  // 文件下载不限流
        }
    }

    /**
     * 静态资源配置：前端 SPA 路由通过 index.html 承接
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}
