package com.toolbox.service.agent.impl;

import com.toolbox.model.agent.ChatEvent;
import com.toolbox.model.agent.ConversationStore;
import com.toolbox.service.agent.*;
import com.toolbox.service.agent.skill.AgentSkill;
import com.toolbox.service.agent.skill.SkillRouter;
import com.toolbox.service.agent.skill.ToolkitContext;
import com.toolbox.service.store.FileStore;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Agent 编排实现 — 管理对话上下文、Skill 路由、运行 ReActAgent、推送 SSE 事件
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class AgentServiceImpl implements AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);

    private final ConversationManager conversationManager;
    private final ConversationStore conversationStore;
    private final SkillRouter skillRouter;
    private final List<AgentSkill> allSkills;
    private final ToolkitContext toolkitContext;
    private final Model agentModel;
    private final String basePrompt;
    private final FileStore fileStore;
    private final ErrorClassifier errorClassifier;

    public AgentServiceImpl(ConversationManager conversationManager,
                            ConversationStore conversationStore,
                            SkillRouter skillRouter,
                            List<AgentSkill> allSkills,
                            ToolkitContext toolkitContext,
                            Model agentModel,
                            String basePrompt,
                            FileStore fileStore,
                            ErrorClassifier errorClassifier) {
        this.conversationManager = conversationManager;
        this.conversationStore = conversationStore;
        this.skillRouter = skillRouter;
        this.allSkills = allSkills;
        this.toolkitContext = toolkitContext;
        this.agentModel = agentModel;
        this.basePrompt = basePrompt;
        this.fileStore = fileStore;
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

        // 2. 幂等性检查: 相同文件 + 相同指令 → 返回缓存结果
        String fingerprint = generateFingerprint(files, message);
        Optional<ConversationStore.CachedResult> cached =
                conversationStore.getCachedResult(conversationId, fingerprint);
        if (cached.isPresent()) {
            ConversationStore.CachedResult r = cached.get();
            log.info("[AgentServiceImpl#handle] cache hit, conv={}, fingerprint={}",
                    conversationId, fingerprint);
            eventConsumer.accept(ChatEvent.thinking("正在分析你的需求..."));
            eventConsumer.accept(ChatEvent.result(r.fileName(), r.fileId(), formatSize(r.size())));
            eventConsumer.accept(ChatEvent.reply(r.replyText()));
            eventConsumer.accept(ChatEvent.done());
            return conversationId;
        }

        try {
        // 3. 文件处理: 存储用户上传的文件
        List<String> fileIds = new ArrayList<>();
        List<String> fileExtensions = new ArrayList<>();
        if (files != null && files.length > 0) {
            if (processFile(files, conversationId, eventConsumer, fileIds, fileExtensions)) {
                return conversationId;
            }
        }

        // 4. Skill 路由: 规则优先 + LLM 兜底
        AgentSkill skill = skillRouter.route(message, fileExtensions);

        // 5. 构建历史（当前消息尚未追加，避免 LLM 看到重复的用户消息）
        List<Msg> history = buildHistory(conversationId);

        // 6. 追加用户消息到对话历史
        conversationManager.appendUserMessage(conversationId, message, fileIds);

        // 7. 构建 Agent 输入（含文件上下文）
        String agentInput = buildAgentInput(message, fileIds, conversationId);

        // 8. 运行 Agent
        log.info("[AgentServiceImpl#handle] sending thinking event, input={}", agentInput);
        eventConsumer.accept(ChatEvent.thinking("正在分析你的需求...", conversationId));
        String finalConvId = conversationId;

        // 设置当前对话 ID，供 @Tool 方法存储产物时使用
        toolkitContext.setCurrentConversationId(conversationId);

        try {
            // 构建当前用户消息
            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .textContent(agentInput)
                    .build();

            List<Msg> messages = new ArrayList<>(history);
            messages.add(userMsg);

            Msg result;
            if (skill != null) {
                // Skill 匹配: 构建专属 Agent 执行
                log.info("[AgentServiceImpl#handle] routing to skill '{}'", skill.name());
                result = executeSkillAgent(skill, messages);
            } else {
                // 无匹配: 使用全部 Skill 工具 + base prompt
                log.info("[AgentServiceImpl#handle] no skill matched, using all skills");
                result = executeSkillAgent(null, messages);
            }

            log.info("[AgentServiceImpl#handle] agent returned, hasText={}",
                    result != null && result.getTextContent() != null);

            // 9. 检查工具产物（如果有则推送 result 事件）
            ToolkitContext.ToolResult toolResult = toolkitContext.getLastResult(finalConvId);
            if (toolResult != null) {
                log.info("[AgentServiceImpl#handle] tool produced file: {} ({} bytes)",
                        toolResult.fileName(), toolResult.size());
                eventConsumer.accept(ChatEvent.result(
                        toolResult.fileName(), toolResult.fileId(),
                        formatSize(toolResult.size())));
            }

            // 10. 提取回复文本
            String replyText = extractReply(result);
            log.info("[AgentServiceImpl#handle] reply text ({} chars): {}",
                    replyText.length(), replyText);
            eventConsumer.accept(ChatEvent.reply(replyText));

            // 11. 缓存结果（用于幂等性去重）
            if (toolResult != null) {
                conversationStore.cacheResult(finalConvId, fingerprint,
                        new ConversationStore.CachedResult(
                                toolResult.fileId(), toolResult.fileName(),
                                toolResult.size(), replyText, System.currentTimeMillis()));
            }

            // 12. 追加助手回复到对话历史（含结果文件信息，供后续请求引用）
            List<String> resultFileIds = toolResult != null
                    ? List.of(toolResult.fileId()) : List.of();
            // 在回复文本中附加文件名信息，让 Agent 知道文件类型
            String historyText = replyText;
            if (toolResult != null) {
                historyText += "\n[结果文件: " + toolResult.fileId()
                        + " (" + toolResult.fileName() + ")]";
            }
            conversationManager.appendAssistantMessage(finalConvId, historyText, resultFileIds);

        } catch (Exception e) {
            log.error("[AgentServiceImpl#handle] agent run failed, convId={}", finalConvId, e);
            eventConsumer.accept(ChatEvent.error(errorClassifier.classify(e)));
        } finally {
            // 确保清理工具产物，防止异常/超时/取消场景下的内存泄漏
            toolkitContext.clearResult(finalConvId);
        }

        } catch (Exception e) {
            // 文件处理/路由/历史/输入构建等任何环节异常 →
            // 依据真实异常给用户反馈，避免用户一直等待
            log.error("[AgentServiceImpl#handle] processing failed, convId={}", conversationId, e);
            eventConsumer.accept(ChatEvent.error(errorClassifier.classify(e)));
        }

        log.info("[AgentServiceImpl#handle] sending done event");
        eventConsumer.accept(ChatEvent.done());
        return conversationId;
    }

    /**
     * 构建 Skill Agent 并执行
     * <p>
     * 注册所有 Skill 的 @Tool 方法（不是只注册匹配的 Skill），
     * 以支持跨 Skill 的多步骤链式调用（如 docToPdf → pdfSplit → pdfToImage）。
     * Skill Prompt 用于聚焦 Agent 注意力到最相关的领域。
     */
    private Msg executeSkillAgent(AgentSkill skill, List<Msg> messages) {
        // 拼接 Prompt: base + skill 片段（聚焦注意力），无 skill 时只用 base
        String sysPrompt = skill != null
                ? basePrompt + "\n\n" + skill.promptFragment()
                : basePrompt;

        // 注册所有 Skill 的 @Tool 方法
        Toolkit allSkillTools = new Toolkit();
        for (AgentSkill s : allSkills) {
            for (Object toolInstance : s.toolInstances()) {
                allSkillTools.registerTool(toolInstance);
            }
        }

        // 构建 Skill Agent
        String agentName = skill != null ? "skill-" + skill.name() : "skill-general";
        ReActAgent skillAgent = ReActAgent.builder()
                .name(agentName)
                .sysPrompt(sysPrompt)
                .model(agentModel)
                .toolkit(allSkillTools)
                .maxIters(8)
                .build();

        return skillAgent.call(messages, RuntimeContext.empty())
                .block(Duration.ofSeconds(120));
    }

    private boolean processFile(MultipartFile[] files, String conversationId,
                                Consumer<ChatEvent> eventConsumer,
                                List<String> fileIds, List<String> fileExtensions) {
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) {
                try {
                    String fileId = fileStore.store(f);
                    fileIds.add(fileId);
                    log.info("[AgentServiceImpl#handle] file stored: {} → {}",
                            f.getOriginalFilename(), fileId);

                    // 提取文件扩展名用于路由
                    String originalName = f.getOriginalFilename();
                    if (originalName != null && originalName.contains(".")) {
                        String ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
                        fileExtensions.add(ext);
                    }
                } catch (Exception e) {
                    eventConsumer.accept(ChatEvent.error(errorClassifier.classify(e)));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 构建包含文件上下文的 Agent 输入
     * <p>
     * 当前请求有文件 → 列出当前文件
     * 当前请求无文件 → 从历史中提取最近上传的文件 ID，让 Agent 能引用之前的文件
     */
    private String buildAgentInput(String message, List<String> fileIds,
                                   String conversationId) {
        // 当前请求有文件
        if (!fileIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("用户上传了以下文件:\n");
            for (String fileId : fileIds) {
                sb.append("- ").append(fileId).append("\n");
            }
            sb.append("\n用户消息: ").append(message);
            sb.append("\n\n使用 fileId 参数调用对应工具。");
            return sb.toString();
        }

        // 当前请求无文件 → 从历史中查找最近上传的文件
        List<String> recentFileIds = getRecentFileIds(conversationId);
        if (!recentFileIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("用户之前上传过以下文件（可直接引用，无需重新上传）:\n");
            for (String fileId : recentFileIds) {
                sb.append("- ").append(fileId).append("\n");
            }
            sb.append("\n用户消息: ").append(message);
            sb.append("\n\n如果用户提到'刚才的文件''之前的文件'等，请直接使用上述 fileId。");
            return sb.toString();
        }

        return message;
    }

    /**
     * 从对话历史中提取最近的文件 ID（最近 5 轮内）
     * 包括用户上传的文件和助手生成的结果文件
     */
    private List<String> getRecentFileIds(String conversationId) {
        var messages = conversationManager.getHistory(conversationId);
        int start = Math.max(0, messages.size() - 10); // 最近 5 轮 = 10 条消息
        List<String> fileIds = new ArrayList<>();
        for (int i = start; i < messages.size(); i++) {
            var m = messages.get(i);
            if (m.fileIds() != null && !m.fileIds().isEmpty()) {
                fileIds.addAll(m.fileIds());
            }
        }
        return fileIds;
    }

    /**
     * 构建最近 N 轮对话历史（含文件上下文）
     */
    private List<Msg> buildHistory(String conversationId) {
        List<Msg> result = new ArrayList<>();
        var messages = conversationManager.getHistory(conversationId);
        // 只取最近 10 轮（20 条消息）
        int start = Math.max(0, messages.size() - 20);
        for (int i = start; i < messages.size(); i++) {
            var m = messages.get(i);
            MsgRole role = "user".equals(m.role()) ? MsgRole.USER : MsgRole.ASSISTANT;

            // 构建消息文本：附带文件信息
            String content = m.content();
            if (m.fileIds() != null && !m.fileIds().isEmpty()) {
                String prefix = "user".equals(m.role()) ? "上传文件" : "生成文件";
                content = "[" + prefix + ": " + String.join(", ", m.fileIds()) + "]\n" + content;
            }

            result.add(Msg.builder()
                    .name(m.role())
                    .role(role)
                    .textContent(content)
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

    /**
     * 生成请求指纹 — 用于幂等性去重
     */
    private String generateFingerprint(MultipartFile[] files, String message) {
        if (files == null || files.length == 0) {
            return "||" + (message != null ? message.trim() : "");
        }
        String filePart = Arrays.stream(files)
                .filter(f -> f != null && !f.isEmpty())
                .map(f -> f.getOriginalFilename() + ":" + f.getSize())
                .sorted()
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
        return filePart + "||" + (message != null ? message.trim() : "");
    }
}
