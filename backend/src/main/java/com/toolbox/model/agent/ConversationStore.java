package com.toolbox.model.agent;

import java.util.List;
import java.util.Optional;

/**
 * 对话存储抽象接口 — 一期内存实现，预留 Redis/JDBC 扩展
 *
 * @author toolbox
 * @since 2026-07-15
 */
public interface ConversationStore {

    /** 创建新对话，返回 conversationId */
    String create();

    /** 追加消息到对话 */
    void append(String conversationId, ConversationMessage message);

    /** 获取对话全部消息 */
    List<ConversationMessage> getMessages(String conversationId);

    /** 查找对话 */
    Optional<ConversationEntry> findById(String conversationId);

    /** 删除对话 */
    void delete(String conversationId);

    /** 列出所有活跃对话 */
    List<ConversationEntry> listActive();

    /**
     * 单条对话消息
     */
    record ConversationMessage(
        String role,            // "user" | "assistant"
        String content,         // 文本内容
        List<String> fileIds,   // 关联文件 ID
        long timestamp
    ) {}

    /**
     * 对话摘要条目
     */
    record ConversationEntry(
        String conversationId,
        String title,           // 首条用户消息的前 50 字
        int roundCount,
        long createdAt,
        long lastActiveAt
    ) {}

    /**
     * 缓存的请求结果 — 用于幂等性去重
     */
    record CachedResult(
        String fileId,
        String fileName,
        long size,
        String replyText,
        long timestamp
    ) {}

    /**
     * 缓存请求结果（按 conversationId + fingerprint 存储）
     */
    default void cacheResult(String conversationId, String fingerprint,
                             CachedResult result) {
        // 默认空实现，子类按需覆写
    }

    /**
     * 查询缓存结果
     */
    default Optional<CachedResult> getCachedResult(String conversationId, String fingerprint) {
        return Optional.empty();
    }
}
