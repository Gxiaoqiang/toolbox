package com.toolbox.service.pdf.impl;

import com.toolbox.service.pdf.AdDomainProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 静态广告域名提供者
 * 内置常见广告平台域名黑名单
 *
 * @author toolbox
 * @since 2026-07-19
 */
@Component
public class StaticAdDomainProvider implements AdDomainProvider {

    /** 内置广告域名列表 */
    private static final List<String> AD_DOMAINS = List.of(
            // Google Ads
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "googletagmanager.com",
            "googletagservices.com",
            "adservice.google.com",
            "pagead2.googlesyndication.com",
            // 百度联盟
            "pos.baidu.com",
            "cpro.baidu.com",
            "drmcmm.baidu.com",
            "hm.baidu.com",
            // CSDN 广告
            "csdnimg.cn/a/",
            // 通用广告平台
            "adnxs.com",
            "adsrvr.org",
            "demdex.net",
            "amazon-adsystem.com",
            "facebook.net/tr",
            "analytics.google.com",
            "ads-twitter.com",
            // 弹窗/推送
            "push.zhanzhang.baidu.com",
            "dup.baidustatic.com"
    );

    @Override
    public List<String> getAdDomains() {
        return AD_DOMAINS;
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
