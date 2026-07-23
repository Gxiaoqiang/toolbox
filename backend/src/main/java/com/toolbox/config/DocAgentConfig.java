package com.toolbox.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolbox.model.agent.ConversationStore;
import com.toolbox.service.agent.*;
import com.toolbox.service.agent.impl.*;
import com.toolbox.service.agent.skill.*;
import com.toolbox.service.document.DocumentService;
import com.toolbox.service.image.ImageToPdfService;
import com.toolbox.service.markdown.MarkdownService;
import com.toolbox.service.pdf.*;
import com.toolbox.service.store.FileStore;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * AgentScope Bean 配置 — 组装 Agent 所需全部组件
 *
 * @author toolbox
 * @since 2026-07-15
 */
@Configuration
public class DocAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(DocAgentConfig.class);

    @Value("${toolbox.agent.llm-provider:dashscope}")
    private String llmProvider;

    @Value("${toolbox.agent.llm-model:qwen-plus}")
    private String llmModel;

    @Value("${toolbox.agent.llm-api-key:}")
    private String llmApiKey;

    @Value("${toolbox.agent.llm-base-url:}")
    private String llmBaseUrl;

    @Value("${toolbox.agent.conversation.max-rounds:50}")
    private int conversationMaxRounds;

    // SSE / file / conversation TTL @Value 已移至 StoreConfig.java
    // 存储层 Bean 已移至 StoreConfig.java

    /**
     * 共享心跳线程池 — 所有 SSE 连接复用，避免每次 new SingleThreadScheduledExecutor
     */
    @Bean
    public ScheduledExecutorService heartbeatExecutor() {
        return Executors.newScheduledThreadPool(4);
    }

    @Bean
    public ConversationManager conversationManager(ConversationStore store) {
        return new ConversationManager(store, conversationMaxRounds);
    }

    // ===== Skill 架构 Beans =====

    @Bean
    public ToolkitContext toolkitContext(FileStore fileStore) {
        return new ToolkitContext(fileStore);
    }

    @Bean
    public PdfSkill pdfSkill(ToolkitContext ctx, PdfService pdfService,
                              PdfCompressService pdfCompressService,
                              PdfToImageService pdfToImageService,
                              PdfArrangeService pdfArrangeService,
                              PdfEncryptService pdfEncryptService,
                              ObjectMapper objectMapper) {
        return new PdfSkill(ctx, pdfService, pdfCompressService,
                pdfToImageService, pdfArrangeService, pdfEncryptService, objectMapper);
    }

    @Bean
    public DocumentSkill documentSkill(ToolkitContext ctx, DocumentService documentService,
                                        MarkdownService markdownService) {
        return new DocumentSkill(ctx, documentService, markdownService);
    }

    @Bean
    public ImageSkill imageSkill(ToolkitContext ctx, ImageToPdfService imageToPdfService) {
        return new ImageSkill(ctx, imageToPdfService);
    }

    @Bean
    public WebSkill webSkill(ToolkitContext ctx, HtmlToPdfService htmlToPdfService) {
        return new WebSkill(ctx, htmlToPdfService);
    }

    @Bean
    public SkillRouter skillRouter(List<AgentSkill> skills, Model agentModel) {
        return new SkillRouter(skills, agentModel);
    }

    // ===== Agent 核心 Beans =====

    @Bean
    public ErrorClassifier errorClassifier() {
        return new ErrorClassifier();
    }

    @Bean
    public Model agentModel() {
        if (llmApiKey == null || llmApiKey.isBlank()) {
            log.warn("[DocAgentConfig#agentModel] LLM API Key not configured, " +
                    "agent LLM calls will fail");
        }
        if ("deepseek".equalsIgnoreCase(llmProvider)) {
            // DeepSeek 兼容 OpenAI API
            log.info("[DocAgentConfig#agentModel] using DeepSeek provider, model={}, baseUrl={}",
                    llmModel, baseUrlOrDefault("https://api.deepseek.com"));
            return buildOpenAiModel("https://api.deepseek.com");
        }
        if ("openai".equalsIgnoreCase(llmProvider)) {
            log.info("[DocAgentConfig#agentModel] using OpenAI provider, model={}, baseUrl={}",
                    llmModel, baseUrlOrDefault("https://api.openai.com"));
            return buildOpenAiModel("https://api.openai.com");
        }
        // 默认: DashScope (阿里云百炼)
        log.info("[DocAgentConfig#agentModel] using DashScope provider, model={}", llmModel);
        return DashScopeChatModel.builder()
                .apiKey(llmApiKey)
                .modelName(llmModel)
                .stream(true)
                .build();
    }

    /**
     * 默认 ReActAgent — 仅用于 AgentController.cancel() 中断请求
     * 实际工具执行由 Skill Agent 动态构建
     */
    @Bean
    public ReActAgent docAgent(Model agentModel) {
        return ReActAgent.builder()
                .name("doc-assistant")
                .sysPrompt("你是文档处理助手。")
                .model(agentModel)
                .toolkit(new io.agentscope.core.tool.Toolkit())
                .maxIters(1)
                .build();
    }

    @Bean
    public AgentService agentService(ConversationManager conversationManager,
                                      ConversationStore conversationStore,
                                      SkillRouter skillRouter,
                                      List<AgentSkill> allSkills,
                                      ToolkitContext toolkitContext,
                                      Model agentModel,
                                      FileStore fileStore,
                                      ErrorClassifier errorClassifier) {
        String basePrompt = loadSystemPrompt();
        return new AgentServiceImpl(conversationManager, conversationStore,
                skillRouter, allSkills, toolkitContext, agentModel,
                basePrompt, fileStore, errorClassifier);
    }

    /** 配置的 baseUrl 优先，否则用默认值 */
    private String baseUrlOrDefault(String defaultUrl) {
        return (llmBaseUrl != null && !llmBaseUrl.isBlank()) ? llmBaseUrl : defaultUrl;
    }

    /** 构建 OpenAI 兼容 Model（DeepSeek / OpenAI / 内网自部署） */
    private Model buildOpenAiModel(String defaultBaseUrl) {
        return OpenAIChatModel.builder()
                .apiKey(llmApiKey)
                .modelName(llmModel)
                .baseUrl(baseUrlOrDefault(defaultBaseUrl))
                .stream(true)
                .build();
    }

    /**
     * 加载 classpath 下的基础系统提示词文件
     */
    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/doc-agent-base.md");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[DocAgentConfig#loadSystemPrompt] failed to load base prompt, " +
                    "using fallback", e);
            return "你是文档处理助手，帮助用户处理 PDF/Word/WPS/Markdown 文件。";
        }
    }
}
