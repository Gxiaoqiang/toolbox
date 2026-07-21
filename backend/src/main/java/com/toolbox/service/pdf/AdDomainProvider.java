package com.toolbox.service.pdf;

import java.util.List;

/**
 * 广告域名提供者接口
 * 支持多种配置源（静态、文件、配置中心）
 *
 * @author toolbox
 * @since 2026-07-19
 */
public interface AdDomainProvider {

    /**
     * 获取当前生效的广告域名列表
     *
     * @return 广告域名列表
     */
    List<String> getAdDomains();

    /**
     * 获取优先级（数字越小优先级越高）
     *
     * @return 优先级
     */
    int getOrder();
}
