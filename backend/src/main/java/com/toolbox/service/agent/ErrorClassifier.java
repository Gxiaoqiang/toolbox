package com.toolbox.service.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异常分类处理 — 将原始异常映射为用户友好的 Agent 回复
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class ErrorClassifier {

    private static final Logger log = LoggerFactory.getLogger(ErrorClassifier.class);

    /**
     * 分类异常并返回用户友好的错误消息
     *
     * @param e 原始异常
     * @return 用户可读的错误描述
     */
    public String classify(Throwable e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";

        // 用户层 — 文件相关
        if (msg.contains("文件为空") || msg.contains("empty")) {
            return "文件为空，请上传有效的文件。";
        }
        if (msg.contains("文件过大") || msg.contains("too large")) {
            return "文件超过了大小限制。PDF 切分/压缩/转图片上限 50MB，合并单个上限 5MB。";
        }
        if (msg.contains("文件不存在") || msg.contains("已过期")) {
            return "文件已过期或不存在，请重新上传。";
        }
        if (msg.contains("加密") || msg.contains("encrypted")) {
            return "该 PDF 有密码保护，请解密后重新上传。";
        }
        if (msg.contains("格式") || msg.contains("format") || msg.contains("不支持")) {
            return "文件格式不支持。支持的格式: PDF / DOC / DOCX / WPS / MD。";
        }

        // 操作层 — 参数/逻辑
        if (msg.contains("至少需要") || msg.contains("too few")) {
            return "文件数量不足。" + msg;
        }
        if (msg.contains("最多") || msg.contains("too many")) {
            return "文件数量超出限制。" + msg;
        }
        if (msg.contains("页") || msg.contains("page")) {
            return "页码范围有误。" + msg;
        }
        if (msg.contains("等级") || msg.contains("level") || msg.contains("1-5")) {
            return "压缩等级无效，有效范围 1-5。";
        }
        if (msg.contains("不可用") || msg.contains("unavailable")) {
            return "文档转 PDF 服务暂不可用（LibreOffice 未启动），请联系管理员。";
        }
        if (msg.contains("轮次") || msg.contains("上限")) {
            return msg;
        }

        // 系统层 — 兜底
        if (msg.contains("timeout") || msg.contains("超时")) {
            log.error("[ErrorClassifier#classify] LLM timeout or processing timeout");
            return "AI 服务响应超时，请稍后重试。";
        }
        if (msg.contains("disk") || msg.contains("空间")) {
            log.error("[ErrorClassifier#classify] disk full");
            return "服务器存储空间不足，请联系管理员。";
        }

        log.error("[ErrorClassifier#classify] unclassified exception", e);
        return "处理时遇到了问题，请稍后重试。如果持续出现，请联系管理员。";
    }
}
