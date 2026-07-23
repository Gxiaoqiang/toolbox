package com.toolbox.service.agent.skill;

import java.util.List;

/**
 * Agent Skill 接口 — 定义一组相关工具的元数据和能力
 * <p>
 * 每个 Skill 封装一类工具（如 PDF 处理、文档转换），提供：
 * - 路由元数据（关键词、文件扩展名）
 * - 工具实例列表（包含 @Tool 注解的方法）
 * - 该 Skill 专属的 Prompt 片段
 *
 * @author toolbox
 * @since 2026-07-22
 */
public interface AgentSkill {

    /**
     * Skill 名称（英文标识）
     */
    String name();

    /**
     * Skill 中文描述（用于路由匹配和日志）
     */
    String description();

    /**
     * 路由关键词 — 用户消息中包含这些词时优先匹配
     */
    List<String> keywords();

    /**
     * 关联文件扩展名 — 用户上传这些类型的文件时优先匹配
     * 格式：".pdf", ".docx" 等（带点）
     */
    List<String> fileExtensions();

    /**
     * 该 Skill 包含的 @Tool 对象实例列表
     */
    List<Object> toolInstances();

    /**
     * 该 Skill 专属的 Prompt 片段（Markdown 格式）
     * 运行时与 base prompt 拼接
     */
    String promptFragment();
}
