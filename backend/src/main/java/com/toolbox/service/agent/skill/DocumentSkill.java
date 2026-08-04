package com.toolbox.service.agent.skill;

import com.toolbox.service.document.DocumentService;
import com.toolbox.service.markdown.MarkdownService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Document Skill — 文档转换工具组
 * <p>
 * 工具列表：docToPdf, mdToDocx
 *
 * @author toolbox
 * @since 2026-07-22
 */
public class DocumentSkill implements AgentSkill {

    private static final Logger log = LoggerFactory.getLogger(DocumentSkill.class);

    private final ToolkitContext ctx;
    private final DocumentService documentService;
    private final MarkdownService markdownService;

    public DocumentSkill(ToolkitContext ctx, DocumentService documentService,
                         MarkdownService markdownService) {
        this.ctx = ctx;
        this.documentService = documentService;
        this.markdownService = markdownService;
    }

    // ===== AgentSkill 接口 =====

    @Override
    public String name() { return "document"; }

    @Override
    public String description() { return "文档转换（Word/WPS/PPT转PDF/Markdown转DOCX）"; }

    @Override
    public List<String> keywords() {
        return List.of("转PDF", "转成PDF", "文档转换", "Word转", "WPS转", "PPT转", "幻灯片转",
                "转DOCX", "转Word", "Markdown转");
    }

    @Override
    public List<String> fileExtensions() { return List.of(".doc", ".docx", ".wps", ".ppt", ".pptx", ".md"); }

    @Override
    public List<Object> toolInstances() { return List.of(this); }

    @Override
    public String promptFragment() { return ToolkitContext.loadPrompt("prompts/skill-document.md"); }

    // ===== @Tool 方法 =====

    @Tool(name = "docToPdf", description = "将 Word(.doc/.docx)、WPS(.wps)或 PPT(.ppt/.pptx)文档转换为 PDF")
    public String docToPdf(
            @ToolParam(name = "fileId", required = true, description = "上传的文档文件 ID")
            String fileId) {

        if (!documentService.isServiceAvailable()) {
            return "错误: 文档转 PDF 服务暂不可用（LibreOffice 未启动），请联系管理员。";
        }

        log.info("[DocumentSkill#docToPdf] converting file: {}", fileId);

        byte[] docBytes = ctx.loadFile(fileId);
        String baseName = ToolkitContext.extractBaseName(fileId);
        String ext = fileId.contains(".") ? fileId.substring(fileId.lastIndexOf('.')) : ".docx";
        try {
            byte[] result = documentService.convertToPdf(docBytes, baseName + ext);
            String resultId = ctx.storeFile(result, baseName + ".pdf");
            ctx.putResult(ctx.getCurrentConversationId(),
                    new ToolkitContext.ToolResult(resultId, baseName + ".pdf", result.length));
            return String.format("转换完成！文件 ID: %s, 大小: %.1fMB",
                    resultId, result.length / (1024.0 * 1024.0));
        } catch (Exception e) {
            log.error("[DocumentSkill#docToPdf] conversion failed", e);
            return "文档转 PDF 失败: LibreOffice 无法转换此文件。"
                    + "请确认文件是有效的 .doc/.docx/.wps/.ppt/.pptx 格式，且没有损坏。"
                    + "也可以尝试用 Word/WPS/PowerPoint 另存为后再上传。";
        }
    }

    @Tool(name = "mdToDocx", description = "将 Markdown 文本转换为 DOCX 文件")
    public String mdToDocx(
            @ToolParam(name = "markdownContent", required = true, description = "Markdown 文本内容")
            String markdownContent,
            @ToolParam(name = "outputName", description = "输出文件名（不含扩展名），默认 output")
            String outputName) {

        if (outputName == null || outputName.isBlank()) outputName = "output";

        log.info("[DocumentSkill#mdToDocx] converting markdown, length={}", markdownContent.length());

        byte[] result = markdownService.convertMarkdownToDocx(markdownContent);
        String resultId = ctx.storeFile(result, outputName + ".docx");
        ctx.putResult(ctx.getCurrentConversationId(),
                new ToolkitContext.ToolResult(resultId, outputName + ".docx", result.length));
        return String.format("转换完成！%d 字符 → DOCX, 文件 ID: %s, 大小: %.1fKB",
                markdownContent.length(), resultId, result.length / 1024.0);
    }
}
