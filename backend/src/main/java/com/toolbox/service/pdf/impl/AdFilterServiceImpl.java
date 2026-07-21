package com.toolbox.service.pdf.impl;

import com.toolbox.service.pdf.AdDomainProvider;
import com.toolbox.service.pdf.AdFilterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 广告过滤服务实现
 * 基于域名黑名单拦截 + CSS 元素隐藏
 *
 * @author toolbox
 * @since 2026-07-19
 */
@Service
public class AdFilterServiceImpl implements AdFilterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdFilterServiceImpl.class);

    /** 合并后的广告域名列表 */
    private final List<String> adDomains;

    /**
     * 构造方法，注入所有广告域名提供者并合并去重
     *
     * @param providers 广告域名提供者列表（按 @Order 排序）
     */
    public AdFilterServiceImpl(List<AdDomainProvider> providers) {
        this.adDomains = providers.stream()
                .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .flatMap(p -> p.getAdDomains().stream())
                .distinct()
                .collect(Collectors.toUnmodifiableList());
        LOGGER.info("[AdFilterServiceImpl#init] loaded {} ad domains from {} providers",
                adDomains.size(), providers.size());
    }

    /**
     * 判断 URL 是否匹配广告域名
     * 支持精确匹配和子域名匹配
     *
     * @param url 请求 URL
     * @return true 表示应拦截
     */
    @Override
    public boolean isAdDomain(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lowerUrl = url.toLowerCase();
        return adDomains.stream().anyMatch(lowerUrl::contains);
    }

    /**
     * 生成广告元素隐藏 CSS
     * 内置通用广告选择器 + 用户自定义选择器
     *
     * @param customHideCss 用户自定义 CSS 选择器
     * @return 完整的 CSS 规则字符串
     */
    @Override
    public String getHideCss(String customHideCss) {
        StringBuilder css = new StringBuilder();

        // 内置通用广告元素选择器
        css.append("""
                .ad, .ads, .adsbygoogle, .ad-container, .ad-wrapper,
                .advertisement, .ad-banner, .ad-slot, .ad-unit,
                [id^="ad-"], [id^="ads-"], [class*="ad-"], [class*="ads-"],
                iframe[src*="doubleclick.net"], iframe[src*="googlesyndication.com"],
                iframe[src*="googleadservices.com"], iframe[src*="adnxs.com"],
                .sponsor, .sponsored, .promo, .promotion,
                [data-ad], [data-ads], [data-ad-slot],
                .cnzz, .bdshare, .baidu-tuijian
                { display: none !important; visibility: hidden !important; }
                """);

        // 用户自定义 CSS
        if (customHideCss != null && !customHideCss.isBlank()) {
            css.append(customHideCss.trim())
               .append(" { display: none !important; visibility: hidden !important; }\n");
        }

        return css.toString();
    }
}
