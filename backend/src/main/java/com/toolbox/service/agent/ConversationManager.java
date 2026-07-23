package com.toolbox.service.agent;

import com.toolbox.model.agent.ConversationStore;
import com.toolbox.model.agent.ConversationStore.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 对话生命周期管理 — 创建、追加消息、对话上限控制
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class ConversationManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationManager.class);

    private final ConversationStore store;
    private final int maxRounds;

    public ConversationManager(ConversationStore store, int maxRounds) {
        this.store = store;
        this.maxRounds = maxRounds;
    }

    /**
     * 创建新对话
     */
    public String create() {
        return store.create();
    }

    /**
     * 追加用户消息
     */
    public void appendUserMessage(String conversationId, String message,
                                   List<String> fileIds) {
        checkRoundLimit(conversationId);
        store.append(conversationId, new ConversationMessage(
            "user", message, fileIds != null ? fileIds : List.of(),
            System.currentTimeMillis()));
    }

    /**
     * 追加助手消息
     */
    public void appendAssistantMessage(String conversationId, String message) {
        store.append(conversationId, new ConversationMessage(
            "assistant", message, List.of(), System.currentTimeMillis()));
    }

    /**
     * 追加助手消息（含结果文件 ID，供后续请求引用）
     */
    public void appendAssistantMessage(String conversationId, String message,
                                        List<String> resultFileIds) {
        store.append(conversationId, new ConversationMessage(
            "assistant", message,
            resultFileIds != null ? resultFileIds : List.of(),
            System.currentTimeMillis()));
    }

    /**
     * 获取对话历史（用于构建 LLM 上下文）
     */
    public List<ConversationMessage> getHistory(String conversationId) {
        return store.getMessages(conversationId);
    }

    /**
     * 删除对话
     */
    public void delete(String conversationId) {
        store.delete(conversationId);
    }

    /**
     * 对话是否存在
     */
    public boolean exists(String conversationId) {
        return store.findById(conversationId).isPresent();
    }

    /**
     * 获取活跃对话列表
     */
    public List<ConversationStore.ConversationEntry> listActive() {
        return store.listActive();
    }

    /**
     * 检查对话轮次上限
     */
    private void checkRoundLimit(String conversationId) {
        List<ConversationMessage> msgs = store.getMessages(conversationId);
        if (msgs == null) return;
        long userMsgCount = msgs.stream()
                .filter(m -> "user".equals(m.role()))
                .count();
        if (userMsgCount >= maxRounds) {
            log.warn("[ConversationManager#checkRoundLimit] conversation {} max rounds " +
                    "reached: {}", conversationId, maxRounds);
            throw new IllegalStateException(
                    "对话轮次已达上限（" + maxRounds + "轮），请开启新对话");
        }
    }
}
