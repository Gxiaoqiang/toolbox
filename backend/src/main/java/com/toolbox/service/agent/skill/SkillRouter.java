package com.toolbox.service.agent.skill;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Skill 路由器 — 规则优先 + LLM 兜底
 * <p>
 * 路由优先级（从高到低）：
 * 1. 文件扩展名强信号（.pdf → PdfSkill）
 * 2. 关键词匹配（"切分" → PdfSkill）
 * 3. LLM 轻量分类（~200ms）
 * 4. 返回 null（无匹配 → 通用回复）
 *
 * @author toolbox
 * @since 2026-07-22
 */
public class SkillRouter {

    private static final Logger log = LoggerFactory.getLogger(SkillRouter.class);

    /** Skill 名称 → AgentSkill 的快速查找表 */
    private final Map<String, AgentSkill> skillMap;
    private final List<AgentSkill> skills;
    private final Model model;

    /** LLM 分类 Prompt — 极简，只需输出一个词 */
    private static final String CLASSIFIER_PROMPT = """
            你是意图分类器。根据用户消息，从以下选项中选择一个：
            - pdf: PDF 相关操作（切分/合并/压缩/转图片/编排/加密）
            - document: 文档转换（Word转PDF/Markdown转DOCX）
            - image: 图片转 PDF
            - web: 网页/URL 转 PDF
            - none: 闲聊/问候/无法判断

            只回复一个词，不要解释。""";

    public SkillRouter(List<AgentSkill> skills, Model model) {
        this.skills = skills;
        this.model = model;
        // 构建 name → skill 查找表
        this.skillMap = new java.util.LinkedHashMap<>();
        for (AgentSkill skill : skills) {
            skillMap.put(skill.name(), skill);
        }

        log.info("[SkillRouter] initialized with {} skills: {}",
                skills.size(), skillMap.keySet());
    }

    /**
     * 根据用户消息和上传文件的扩展名路由到最匹配的 Skill
     *
     * @param message        用户消息文本
     * @param fileExtensions 上传文件的扩展名列表（如 [".pdf", ".docx"]）
     * @return 匹配的 Skill，无匹配返回 null
     */
    public AgentSkill route(String message, List<String> fileExtensions) {
        if (message == null) message = "";
        String msgLower = message.toLowerCase();

        // 优先级 1: 文件扩展名强信号
        AgentSkill byExt = matchByExtension(fileExtensions);
        if (byExt != null) return byExt;

        // 优先级 2: 关键词匹配
        AgentSkill byKeyword = matchByKeyword(msgLower);
        if (byKeyword != null) return byKeyword;

        // 优先级 3: LLM 兜底分类
        AgentSkill byLlm = classifyByLlm(message);
        if (byLlm != null) return byLlm;

        // 无匹配
        log.info("[SkillRouter#route] no skill matched for message: {}",
                message.length() > 50 ? message.substring(0, 50) + "..." : message);
        return null;
    }

    /**
     * 文件扩展名匹配
     */
    private AgentSkill matchByExtension(List<String> fileExtensions) {
        if (fileExtensions == null || fileExtensions.isEmpty()) return null;
        for (AgentSkill skill : skills) {
            for (String ext : skill.fileExtensions()) {
                if (fileExtensions.contains(ext)) {
                    log.info("[SkillRouter] matched by file ext '{}' → '{}'", ext, skill.name());
                    return skill;
                }
            }
        }
        return null;
    }

    /**
     * 关键词匹配
     */
    private AgentSkill matchByKeyword(String msgLower) {
        for (AgentSkill skill : skills) {
            for (String keyword : skill.keywords()) {
                if (msgLower.contains(keyword.toLowerCase())) {
                    log.info("[SkillRouter] matched by keyword '{}' → '{}'", keyword, skill.name());
                    return skill;
                }
            }
        }
        return null;
    }

    /**
     * LLM 轻量分类 — 仅在规则未命中时调用
     * 每次创建新 Agent 实例，保证线程安全
     */
    private AgentSkill classifyByLlm(String message) {
        try {
            // 每次调用创建新实例，避免并发状态冲突
            ReActAgent classifier = ReActAgent.builder()
                    .name("skill-classifier")
                    .sysPrompt(CLASSIFIER_PROMPT)
                    .model(model)
                    .toolkit(new Toolkit())
                    .maxIters(1)
                    .build();

            Msg msg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .textContent(message)
                    .build();

            Msg result = classifier.call(List.of(msg), RuntimeContext.empty())
                    .block(Duration.ofSeconds(5));

            if (result == null || result.getTextContent() == null) {
                log.info("[SkillRouter] LLM returned empty response");
                return null;
            }

            String answer = result.getTextContent().trim().toLowerCase();
            log.info("[SkillRouter] LLM classification: '{}'", answer);

            // 解析 LLM 响应
            if (answer.contains("none") || answer.contains("无法判断")) {
                return null;
            }

            // 从响应中提取 skill name
            for (String skillName : skillMap.keySet()) {
                if (answer.contains(skillName)) {
                    AgentSkill skill = skillMap.get(skillName);
                    log.info("[SkillRouter] matched by LLM → '{}'", skill.name());
                    return skill;
                }
            }

            log.info("[SkillRouter] LLM response '{}' did not match any skill", answer);
            return null;

        } catch (Exception e) {
            log.warn("[SkillRouter] LLM classification failed, falling back to null", e);
            return null;
        }
    }
}
