package com.toolbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 异常文案配置
 * <p>
 * 通过 {@code application.yml} 的 {@code toolbox.agent.error.*} 配置，
 * 将异常分类的关键词与用户提示文案外部化，避免硬编码到代码中。
 *
 * @author toolbox
 * @since 2026-08-03
 */
@Component
@ConfigurationProperties(prefix = "toolbox.agent.error")
public class AgentErrorProperties {

    /** 兜底文案：未命中任何规则时返回 */
    private String fallbackMessage = "处理时遇到了问题，请稍后重试。如果持续出现，请联系管理员。";

    /** 错误分类规则，按顺序匹配，先命中先用 */
    private List<ErrorRule> rules = new ArrayList<>();

    public String getFallbackMessage() {
        return fallbackMessage;
    }

    public void setFallbackMessage(String fallbackMessage) {
        this.fallbackMessage = fallbackMessage;
    }

    public List<ErrorRule> getRules() {
        return rules;
    }

    public void setRules(List<ErrorRule> rules) {
        this.rules = rules;
    }
}
