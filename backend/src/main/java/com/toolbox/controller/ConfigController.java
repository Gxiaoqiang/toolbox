package com.toolbox.controller;

import com.toolbox.model.common.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 应用配置接口 — 供前端判断各功能是否启用
 * <p>
 * 该接口始终可用（不受 {@code toolbox.agent.enabled} 影响），
 * 前端据此决定是否展示"文档助手"等可配置功能。
 *
 * @author toolbox
 * @since 2026-08-04
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${toolbox.agent.enabled:true}")
    private boolean agentEnabled;

    /**
     * 获取功能开关列表
     * <p>
     * key 与前端工具 id 一致（如 "doc-agent"），前端据此隐藏被关闭的工具。
     *
     * @return 形如 {"doc-agent": true} 的功能开关映射
     */
    @GetMapping("/features")
    public R<Map<String, Object>> features() {
        return R.ok(Map.of("doc-agent", agentEnabled));
    }
}
