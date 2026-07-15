package com.toolbox.model.agent;

/**
 * Agent 对话请求 DTO
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class ChatRequest {

    /** 用户消息文本 */
    private String message;

    /** 对话 ID（可选，新对话不传） */
    private String conversationId;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
}
