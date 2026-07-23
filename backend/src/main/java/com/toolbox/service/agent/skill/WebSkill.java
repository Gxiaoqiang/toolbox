package com.toolbox.service.agent.skill;

import com.toolbox.exception.BusinessException;
import com.toolbox.service.pdf.HtmlToPdfService;
import com.toolbox.service.pdf.RenderContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Web Skill — 网页/HTML 转 PDF 工具组
 * <p>
 * 工具列表：htmlToPdf
 *
 * @author toolbox
 * @since 2026-07-22
 */
public class WebSkill implements AgentSkill {

    private static final Logger log = LoggerFactory.getLogger(WebSkill.class);

    private final ToolkitContext ctx;
    private final HtmlToPdfService htmlToPdfService;

    public WebSkill(ToolkitContext ctx, HtmlToPdfService htmlToPdfService) {
        this.ctx = ctx;
        this.htmlToPdfService = htmlToPdfService;
    }

    // ===== AgentSkill 接口 =====

    @Override
    public String name() { return "web"; }

    @Override
    public String description() { return "网页/HTML 转 PDF"; }

    @Override
    public List<String> keywords() {
        return List.of("网页转PDF", "URL转PDF", "HTML转PDF", "网页转", "网址转");
    }

    @Override
    public List<String> fileExtensions() { return List.of(".html", ".htm"); }

    @Override
    public List<Object> toolInstances() { return List.of(this); }

    @Override
    public String promptFragment() { return ToolkitContext.loadPrompt("prompts/skill-web.md"); }

    // ===== @Tool 方法 =====

    @Tool(name = "htmlToPdf", description = "将网页 URL 或本地 HTML 文件转换为 PDF。支持去广告、自定义纸张/边距/视口等参数")
    public String htmlToPdf(
            @ToolParam(name = "url", description = "目标网页 URL（与 fileId 二选一）")
            String url,
            @ToolParam(name = "fileId", description = "上传的 HTML 文件 ID（与 url 二选一）")
            String fileId,
            @ToolParam(name = "paperSize", description = "纸张大小: A4/Letter/Legal，默认 A4")
            String paperSize,
            @ToolParam(name = "orientation", description = "方向: portrait(纵向)/landscape(横向)，默认 portrait")
            String orientation,
            @ToolParam(name = "margin", description = "边距: none(无)/narrow(窄)/medium(中)/wide(宽)，默认 medium")
            String margin,
            @ToolParam(name = "scale", description = "缩放比例 50-200，默认 100")
            Integer scale,
            @ToolParam(name = "viewport", description = "视口: desktop(1280px)/tablet(768px)/mobile(375px)，默认 desktop")
            String viewport,
            @ToolParam(name = "removeAds", description = "是否去广告，默认 true")
            Boolean removeAds,
            @ToolParam(name = "footerMode", description = "页脚: none(无)/pageNumber(页码)/date(日期)，默认 pageNumber")
            String footerMode) {

        if (paperSize == null || paperSize.isBlank()) paperSize = "A4";
        if (orientation == null || orientation.isBlank()) orientation = "portrait";
        if (margin == null || margin.isBlank()) margin = "medium";
        if (scale == null) scale = 100;
        if (viewport == null || viewport.isBlank()) viewport = "desktop";
        if (removeAds == null) removeAds = true;
        if (footerMode == null || footerMode.isBlank()) footerMode = "pageNumber";

        if ((url == null || url.isBlank()) && (fileId == null || fileId.isBlank())) {
            return "错误: 请提供网页 URL 或上传 HTML 文件（url 和 fileId 至少填一个）";
        }

        log.info("[WebSkill#htmlToPdf] url={}, fileId={}, paper={}, orientation={}, viewport={}, removeAds={}",
                url, fileId, paperSize, orientation, viewport, removeAds);

        RenderContext renderCtx = new RenderContext();
        renderCtx.setPaperSize(paperSize);
        renderCtx.setOrientation(orientation);
        renderCtx.setMargin(margin);
        renderCtx.setScale(scale);
        renderCtx.setViewport(viewport);
        renderCtx.setRemoveAds(removeAds);
        renderCtx.setFooterMode(footerMode);

        try {
            byte[] result;

            if (url != null && !url.isBlank()) {
                result = htmlToPdfService.convertUrl(url.trim(), renderCtx);
            } else {
                byte[] htmlBytes = ctx.loadFile(fileId.trim());
                result = htmlToPdfService.convertHtml(htmlBytes, renderCtx);
            }

            String resultId = ctx.storeFile(result, "webpage.pdf");
            ctx.putResult(ctx.getCurrentConversationId(),
                    new ToolkitContext.ToolResult(resultId, "webpage.pdf", result.length));
            return String.format("转换完成！文件 ID: %s, 大小: %.1fMB",
                    resultId, result.length / (1024.0 * 1024.0));
        } catch (BusinessException e) {
            return "转换失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("[WebSkill#htmlToPdf] conversion failed", e);
            return "HTML 转 PDF 失败，请稍后重试。";
        }
    }
}
