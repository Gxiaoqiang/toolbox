package com.toolbox.service.agent.impl;

import com.toolbox.model.agent.ChatEvent;
import com.toolbox.service.agent.*;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Agent 编排实现 — 管理对话上下文、运行 ReActAgent、推送 SSE 事件
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class AgentServiceImpl implements AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);

    private final ReActAgent docAgent;
    private final DocAgentToolkit toolkit;
    private final ConversationManager conversationManager;
    private final FileManager fileManager;
    private final ErrorClassifier errorClassifier;

    public AgentServiceImpl(ReActAgent docAgent, DocAgentToolkit toolkit,
                            ConversationManager conversationManager,
                            FileManager fileManager, ErrorClassifier errorClassifier) {
        this.docAgent = docAgent;
        this.toolkit = toolkit;
        this.conversationManager = conversationManager;
        this.fileManager = fileManager;
        this.errorClassifier = errorClassifier;
    }

    @Override
    public String handle(String message, MultipartFile[] files, String conversationId,
                         Consumer<ChatEvent> eventConsumer) {

        // 1. 对话管理: 创建或复用对话
        if (conversationId == null || conversationId.isBlank()
                || !conversationManager.exists(conversationId)) {
            conversationId = conversationManager.create();
            log.info("[AgentServiceImpl#handle] new conversation: {}", conversationId);
        }

        // 2. 文件处理: 存储用户上传的文件
        List<String> fileIds = new ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    try {
                        String fileId = fileManager.store(f);
                        fileIds.add(fileId);
                        log.info("[AgentServiceImpl#handle] file stored: {} → {}",
                                f.getOriginalFilename(), fileId);
                    } catch (Exception e) {
                        eventConsumer.accept(ChatEvent.error(errorClassifier.classify(e)));
                        return conversationId;
                    }
                }
            }
        }

        // 3. 先构建历史（当前消息尚未追加，避免 LLM 看到重复的用户消息）
        List<Msg> history = buildHistory(conversationId);

        // 4. 追加用户消息到对话历史（工具调用完成后才能 correct 地保留）
        conversationManager.appendUserMessage(conversationId, message, fileIds);

        // 5. 构建 Agent 输入（含文件上下文）
        String agentInput = buildAgentInput(message, fileIds);

        // 6. 运行 ReActAgent
        log.info("[AgentServiceImpl#handle] sending thinking event, input={}", agentInput);
        eventConsumer.accept(ChatEvent.thinking("正在分析你的需求..."));
        String finalConvId = conversationId;

        try {
            // 构建当前用户消息
            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .textContent(agentInput)
                    .build();

            List<Msg> messages = new ArrayList<>(history);
            messages.add(userMsg);

            log.info("[AgentServiceImpl#handle] calling agent with {} history msgs + 1 user msg",
                    history.size());
            // 调用 Agent（ReActAgent.call() 返回 Mono<Msg>，block 等待结果）
            Msg result = docAgent.call(messages, RuntimeContext.empty())
                    .block(Duration.ofSeconds(120));

            log.info("[AgentServiceImpl#handle] agent returned, hasText={}",
                    result != null && result.getTextContent() != null);

            // 7. 检查工具产物（如果有则推送 result 事件）
            DocAgentToolkit.ToolResult toolResult = toolkit.getLastResult();
            if (toolResult != null) {
                log.info("[AgentServiceImpl#handle] tool produced file: {} ({} bytes)",
                        toolResult.fileName(), toolResult.size());
                eventConsumer.accept(ChatEvent.result(
                        toolResult.fileName(), toolResult.fileId(),
                        formatSize(toolResult.size())));
            }

            // 8. 提取回复文本
            String replyText = extractReply(result);
            log.info("[AgentServiceImpl#handle] reply text ({} chars): {}",
                    replyText.length(), replyText);
            eventConsumer.accept(ChatEvent.reply(replyText));

            // 9. 追加助手回复到对话历史
            conversationManager.appendAssistantMessage(finalConvId, replyText);

        } catch (Exception e) {
            log.error("[AgentServiceImpl#handle] agent run failed, convId={}", finalConvId, e);
            eventConsumer.accept(ChatEvent.error(errorClassifier.classify(e)));
        }

        log.info("[AgentServiceImpl#handle] sending done event");
        eventConsumer.accept(ChatEvent.done());
        return conversationId;
    }

    /**
     * 构建包含文件上下文的 Agent 输入
     */
    private String buildAgentInput(String message, List<String> fileIds) {
        if (fileIds.isEmpty()) {
            return message;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户上传了以下文件:\n");
        for (String fileId : fileIds) {
            sb.append("- ").append(fileId).append("\n");
        }
        sb.append("\n用户消息: ").append(message);
        sb.append("\n\n使用 fileId 参数调用对应工具。");
        return sb.toString();
    }

    /**
     * 构建最近 N 轮对话历史
     */
    private List<Msg> buildHistory(String conversationId) {
        List<Msg> result = new ArrayList<>();
        var messages = conversationManager.getHistory(conversationId);
        // 只取最近 10 轮（20 条消息）
        int start = Math.max(0, messages.size() - 20);
        for (int i = start; i < messages.size(); i++) {
            var m = messages.get(i);
            MsgRole role = "user".equals(m.role()) ? MsgRole.USER : MsgRole.ASSISTANT;
            result.add(Msg.builder()
                    .name(m.role())
                    .role(role)
                    .textContent(m.content())
                    .build());
        }
        return result;
    }

    /**
     * 从 Agent 返回的 Msg 中提取文字回复
     */
    private String extractReply(Msg msg) {
        if (msg == null) return "处理完成，请查看结果。";
        String text = msg.getTextContent();
        return (text != null && !text.isBlank()) ? text : "处理完成。";
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
