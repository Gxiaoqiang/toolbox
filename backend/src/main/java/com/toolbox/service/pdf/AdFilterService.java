package com.toolbox.service.pdf;

/**
 * 广告过滤服务接口
 *
 * @author toolbox
 * @since 2026-07-19
 */
public interface AdFilterService {

    /**
     * 判断 URL 是否为广告域名
     *
     * @param url 请求 URL
     * @return true 表示应拦截
     */
    boolean isAdDomain(String url);

    /**
     * 获取广告元素隐藏 CSS（内置 + 用户自定义）
     *
     * @param customHideCss 用户自定义 CSS 选择器
     * @return 完整的 CSS 规则字符串
     */
    String getHideCss(String customHideCss);
}
