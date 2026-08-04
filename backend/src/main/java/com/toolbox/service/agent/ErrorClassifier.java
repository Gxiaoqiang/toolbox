package com.toolbox.service.agent;

import com.toolbox.config.AgentErrorProperties;
import com.toolbox.config.ErrorRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异常分类处理 — 将原始异常映射为用户友好的 Agent 回复
 * <p>
 * 匹配规则（关键词 → 文案）由 {@code application.yml} 配置，数据驱动，避免硬编码。
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class ErrorClassifier {

    private static final Logger log = LoggerFactory.getLogger(ErrorClassifier.class);

    /** 文案模板中的原始异常消息占位符 */
    private static final String MSG_PLACEHOLDER = "{msg}";

    private final AgentErrorProperties props;

    public ErrorClassifier(AgentErrorProperties props) {
        this.props = props;
    }

    /**
     * 分类异常并返回用户友好的错误消息
     *
     * @param e 原始异常
     * @return 用户可读的错误描述
     */
    public String classify(Throwable e) {
        String raw = e.getMessage() != null ? e.getMessage() : "";

        // 按配置顺序匹配规则，先命中先用
        for (ErrorRule rule : props.getRules()) {
            if (matches(rule, raw)) {
                if (rule.logError()) {
                    log.error("[ErrorClassifier#classify] matched rule, raw={}", raw);
                }
                return render(rule.message(), raw);
            }
        }

        log.error("[ErrorClassifier#classify] unclassified exception", e);
        return props.getFallbackMessage();
    }

    /**
     * 判断原始异常消息是否命中任一关键词
     */
    private boolean matches(ErrorRule rule, String raw) {
        if (rule.keywords() == null) {
            return false;
        }
        for (String keyword : rule.keywords()) {
            if (keyword != null && raw.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 渲染文案：将 {@code {msg}} 占位符替换为原始异常消息
     */
    private String render(String template, String raw) {
        return template.replace(MSG_PLACEHOLDER, raw);
    }
}
