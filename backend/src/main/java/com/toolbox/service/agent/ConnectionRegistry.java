package com.toolbox.service.agent;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.function.Consumer;

/**
 * SSE 连接注册表抽象 — 可插拔实现：本地内存 / Redis 共享
 *
 * @author toolbox
 * @since 2026-07-16
 */
public interface ConnectionRegistry {

    /**
     * 注册 SSE 连接。单 conversation 互斥，超出全局上限拒绝。
     *
     * @param conversationId 对话 ID
     * @param emitter        SseEmitter
     * @param cleanup        SSE 断开时的清理回调（心跳 cancel 等）
     * @return true 注册成功，false 被拒绝
     */
    boolean register(String conversationId, SseEmitter emitter, Runnable cleanup);

    /**
     * 主动注销连接
     */
    void unregister(String conversationId);

    /** 当前活跃连接数 */
    int getActiveCount();

    /** 心跳间隔（毫秒） */
    long getHeartbeatIntervalMs();

    /** SSE 连接超时（毫秒） */
    long getConnectionTimeoutMs();

    /**
     * 设置连接断开回调 — 当 SSE 超时/错误/完成时触发，用于清理关联资源
     */
    void setOnDisconnect(Consumer<String> callback);

    /**
     * 查找本机指定对话的 SSE 连接，用于事件投递。
     *
     * @return SseEmitter，未找到返回 null
     */
    SseEmitter getEmitter(String conversationId);

    /**
     * 标记对话为处理中（Agent 开始执行时调用）
     */
    void setProcessing(String conversationId);

    /**
     * 清除处理中标记（Agent 执行完成时调用）
     */
    void clearProcessing(String conversationId);

    /**
     * 查询对话是否正在处理中
     */
    boolean isProcessing(String conversationId);
}
