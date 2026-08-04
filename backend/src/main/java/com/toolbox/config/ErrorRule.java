package com.toolbox.config;

import java.util.List;

/**
 * 错误分类规则 — 关键词命中后返回对应文案
 * <p>
 * 由 application.yml 中 {@code toolbox.agent.error.rules} 配置，
 * 避免把关键词与提示文案硬编码到 Java 代码中。
 *
 * @param keywords 触发关键词（原始异常消息包含任一即命中，顺序敏感）
 * @param message  用户可见文案；可用 {@code {msg}} 占位符引用原始异常消息
 * @param logError 命中时是否额外打印 error 级日志（用于超时、磁盘等系统级错误）
 * @author toolbox
 * @since 2026-08-03
 */
public record ErrorRule(
        List<String> keywords,
        String message,
        boolean logError) {
}
