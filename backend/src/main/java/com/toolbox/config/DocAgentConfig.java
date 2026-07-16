package com.toolbox.config;

import com.toolbox.model.agent.ConversationStore;
import com.toolbox.model.agent.InMemoryConversationStore;
import com.toolbox.service.agent.*;
import com.toolbox.service.agent.impl.AgentServiceImpl;
import com.toolbox.service.document.DocumentService;
import com.toolbox.service.markdown.MarkdownService;
import com.toolbox.service.pdf.*;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

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

    @Value("${toolbox.agent.sse.max-connections:50}")
    private int sseMaxConnections;

    @Value("${toolbox.agent.sse.heartbeat-interval-ms:15000}")
    private long sseHeartbeatMs;

    @Value("${toolbox.agent.sse.connection-timeout-ms:300000}")
    private long sseTimeoutMs;

    @Value("${toolbox.agent.conversation.max-rounds:50}")
    private int conversationMaxRounds;


    //默认12小时清理一次
    @Value("${toolbox.agent.conversation.ttl-minutes:1200}")
    private int conversationTtlMinutes;

    @Value("${toolbox.agent.file.upload-dir:${java.io.tmpdir}/toolbox-agent}")
    private String fileUploadDir;

    @Value("${toolbox.agent.file.max-file-size:52428800}")
    private long fileMaxSize;

    // ===== 基础设施 Beans =====

    @Bean
    public FileManager fileManager() {
        return new FileManager(fileUploadDir, fileMaxSize,
                Duration.ofMinutes(conversationTtlMinutes));
    }

    @Bean
    public SseConnectionManager sseConnectionManager() {
        return new SseConnectionManager(sseMaxConnections, sseHeartbeatMs, sseTimeoutMs);
    }

    @Bean
    public ConversationStore conversationStore() {
        return new InMemoryConversationStore();
    }

    @Bean
    public ConversationManager conversationManager(ConversationStore store) {
        return new ConversationManager(store, conversationMaxRounds);
    }

    // ===== Agent 核心 Beans =====

    @Bean
    public ErrorClassifier errorClassifier() {
        return new ErrorClassifier();
    }

    @Bean
    public DocAgentToolkit docAgentToolkit(
            PdfService pdfService,
            PdfCompressService pdfCompressService,
            PdfToImageService pdfToImageService,
            DocumentService documentService,
            MarkdownService markdownService,
            FileManager fileManager) {
        return new DocAgentToolkit(pdfService, pdfCompressService, pdfToImageService,
                documentService, markdownService, fileManager);
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

    @Bean
    public Toolkit agentToolkit(DocAgentToolkit docAgentToolkit) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(docAgentToolkit);
        log.info("[DocAgentConfig#agentToolkit] registered {} tools", toolkit.getToolNames().size());
        return toolkit;
    }

    @Bean
    public ReActAgent docAgent(Model agentModel, Toolkit agentToolkit) {
        String sysPrompt = loadSystemPrompt();
        return ReActAgent.builder()
                .name("doc-assistant")
                .sysPrompt(sysPrompt)
                .model(agentModel)
                .toolkit(agentToolkit)
                .maxIters(8)
                .build();
    }

    @Bean
    public AgentService agentService(ReActAgent docAgent, DocAgentToolkit toolkit,
                                      ConversationManager conversationManager,
                                      FileManager fileManager,
                                      ErrorClassifier errorClassifier) {
        return new AgentServiceImpl(docAgent, toolkit, conversationManager,
                fileManager, errorClassifier);
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
     * 加载 classpath 下的系统提示词文件
     */
    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/doc-agent-system.md");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[DocAgentConfig#loadSystemPrompt] failed to load system prompt, " +
                    "using fallback", e);
            return "你是文档处理助手，帮助用户处理 PDF/Word/WPS/Markdown 文件。";
        }
    }
}
