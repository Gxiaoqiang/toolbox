package com.toolbox.model.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SSE 事件 DTO — 序列化为 JSON 通过 SSE 发送给前端
 *
 * @author toolbox
 * @since 2026-07-15
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatEvent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum Type {
        thinking, tool_call, progress, result, reply, error, heartbeat, done
    }

    private String type;
    private String text;
    private String tool;
    private String params;
    private String fileName;
    private String fileId;
    private String size;
    private String conversationId;
    private int progress;

    // ===== 工厂方法 =====

    public static ChatEvent thinking(String text) {
        ChatEvent e = new ChatEvent();
        e.type = Type.thinking.name();
        e.text = text;
        return e;
    }

    public static ChatEvent thinking(String text, String conversationId) {
        ChatEvent e = new ChatEvent();
        e.type = Type.thinking.name();
        e.text = text;
        e.conversationId = conversationId;
        return e;
    }

    public static ChatEvent toolCall(String tool, String params) {
        ChatEvent e = new ChatEvent();
        e.type = Type.tool_call.name();
        e.tool = tool;
        e.params = params;
        return e;
    }

    public static ChatEvent result(String fileName, String fileId, String size) {
        ChatEvent e = new ChatEvent();
        e.type = Type.result.name();
        e.fileName = fileName;
        e.fileId = fileId;
        e.size = size;
        return e;
    }

    public static ChatEvent reply(String text) {
        ChatEvent e = new ChatEvent();
        e.type = Type.reply.name();
        e.text = text;
        return e;
    }

    public static ChatEvent error(String text) {
        ChatEvent e = new ChatEvent();
        e.type = Type.error.name();
        e.text = text;
        return e;
    }

    public static ChatEvent heartbeat() {
        ChatEvent e = new ChatEvent();
        e.type = Type.heartbeat.name();
        return e;
    }

    public static ChatEvent done() {
        ChatEvent e = new ChatEvent();
        e.type = Type.done.name();
        return e;
    }

    public static ChatEvent progress(int pct) {
        ChatEvent e = new ChatEvent();
        e.type = Type.progress.name();
        e.progress = pct;
        return e;
    }

    /** 序列化为 JSON 字符串 */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"text\":\"序列化失败\"}";
        }
    }

    // ===== getters =====

    public String getType() { return type; }
    public String getText() { return text; }
    public String getTool() { return tool; }
    public String getParams() { return params; }
    public String getFileName() { return fileName; }
    public String getFileId() { return fileId; }
    public String getSize() { return size; }
    public String getConversationId() { return conversationId; }
    public int getProgress() { return progress; }
}
