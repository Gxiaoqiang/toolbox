package com.toolbox.service.agent.skill;

import com.toolbox.exception.BusinessException;
import com.toolbox.model.common.PdfArrangeItem;
import com.toolbox.model.pdf.DewatermarkRequest;
import com.toolbox.model.pdf.DewatermarkResult;
import com.toolbox.model.pdf.RedactRequest;
import com.toolbox.model.pdf.WatermarkRequest;
import com.toolbox.service.pdf.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * PDF Skill — 包含所有 PDF 相关工具
 * <p>
 * 工具列表：pdfSplit, pdfMerge, pdfCompress, pdfToImage, pdfArrange, pdfInfo, pdfEncrypt
 *
 * @author toolbox
 * @since 2026-07-22
 */
public class PdfSkill implements AgentSkill {

    private static final Logger log = LoggerFactory.getLogger(PdfSkill.class);

    private final ToolkitContext ctx;
    private final PdfService pdfService;
    private final PdfCompressService pdfCompressService;
    private final PdfToImageService pdfToImageService;
    private final PdfArrangeService pdfArrangeService;
    private final PdfEncryptService pdfEncryptService;
    private final PdfWatermarkService pdfWatermarkService;
    private final PdfDewatermarkService pdfDewatermarkService;
    private final PdfRedactService pdfRedactService;
    private final ObjectMapper objectMapper;

    public PdfSkill(ToolkitContext ctx, PdfService pdfService,
                    PdfCompressService pdfCompressService,
                    PdfToImageService pdfToImageService,
                    PdfArrangeService pdfArrangeService,
                    PdfEncryptService pdfEncryptService,
                    PdfWatermarkService pdfWatermarkService,
                    PdfDewatermarkService pdfDewatermarkService,
                    PdfRedactService pdfRedactService,
                    ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.pdfService = pdfService;
        this.pdfCompressService = pdfCompressService;
        this.pdfToImageService = pdfToImageService;
        this.pdfArrangeService = pdfArrangeService;
        this.pdfEncryptService = pdfEncryptService;
        this.pdfWatermarkService = pdfWatermarkService;
        this.pdfDewatermarkService = pdfDewatermarkService;
        this.pdfRedactService = pdfRedactService;
        this.objectMapper = objectMapper;
    }

    // ===== AgentSkill 接口 =====

    @Override
    public String name() { return "pdf"; }

    @Override
    public String description() { return "PDF 处理（切分/合并/压缩/转图片/编排/加密/加水印/去水印/涂黑）"; }

    @Override
    public List<String> keywords() {
        return List.of("切分", "拆分", "分割", "合并", "压缩", "转图片", "转成图片",
                "编排", "排序", "删页", "删除页", "旋转", "加密", "解密", "加密码",
                "水印", "加印", "打水印", "去水印", "去印", "涂黑", "遮盖", "遮挡", "PDF");
    }

    @Override
    public List<String> fileExtensions() { return List.of(".pdf"); }

    @Override
    public List<Object> toolInstances() { return List.of(this); }

    @Override
    public String promptFragment() { return ToolkitContext.loadPrompt("prompts/skill-pdf.md"); }

    // ===== @Tool 方法 =====

    @Tool(name = "pdfSplit", description = "拆分 PDF 文件。mode: by-page(逐页)/by-range(指定范围)/by-n(每N页)")
    public String pdfSplit(
            @ToolParam(name = "fileId", required = true, description = "上传的 PDF 文件 ID")
            String fileId,
            @ToolParam(name = "mode", required = true, description = "拆分模式: by-page / by-range / by-n")
            String mode,
            @ToolParam(name = "pages", description = "页码范围，mode=by-range 时需要，如 '1,3,5-8'")
            String pages,
            @ToolParam(name = "everyN", description = "每 N 页一组，mode=by-n 时需要，默认 1")
            Integer everyN) {

        if (mode == null || mode.isBlank()) mode = "by-page";
        if (everyN == null) everyN = 1;

        log.info("[PdfSkill#pdfSplit] mode={}, pages={}, everyN={}", mode, pages, everyN);

        byte[] pdfBytes = ctx.loadFile(fileId);
        String baseName = ToolkitContext.extractBaseName(fileId);
        byte[] result = pdfService.splitPdf(pdfBytes, baseName + ".pdf", mode,
                pages != null ? pages : "", everyN, true);

        String resultId = ctx.storeFile(result, baseName + "_split.zip");
        ctx.putResult(ctx.getCurrentConversationId(),
                new ToolkitContext.ToolResult(resultId, baseName + "_split.zip", result.length));
        int pageCount = estimatePageCount(pdfBytes);
        return String.format("切分完成！%d 页 PDF 已拆分, 文件 ID: %s, 大小: %.1fMB",
                pageCount, resultId, result.length / (1024.0 * 1024.0));
    }

    @Tool(name = "pdfMerge", description = "合并多个 PDF 文件为一个，2-10 个")
    public String pdfMerge(
            @ToolParam(name = "fileIds", required = true, description = "文件 ID 列表，逗号分隔")
            String fileIds) {

        String[] ids = fileIds.split(",");
        if (ids.length < 2) {
            return "错误: 至少需要 2 个 PDF 文件才能合并，当前只有 " + ids.length + " 个";
        }
        if (ids.length > 10) {
            return "错误: 最多合并 10 个文件，当前 " + ids.length + " 个";
        }

        log.info("[PdfSkill#pdfMerge] merging {} files", ids.length);

        List<byte[]> bytesList = new ArrayList<>();
        for (String id : ids) {
            bytesList.add(ctx.loadFile(id.trim()));
        }

        byte[] result = pdfService.mergePdf(bytesList, true);
        String resultId = ctx.storeFile(result, "merged.pdf");
        ctx.putResult(ctx.getCurrentConversationId(),
                new ToolkitContext.ToolResult(resultId, "merged.pdf", result.length));
        return String.format("合并完成！%d 个文件 → 1 个 PDF, 文件 ID: %s, 大小: %.1fMB",
                ids.length, resultId, result.length / (1024.0 * 1024.0));
    }

    @Tool(name = "pdfCompress", description = "压缩 PDF 文件, level 1(极度压缩)-5(极限画质), 默认 3")
    public String pdfCompress(
            @ToolParam(name = "fileId", required = true, description = "上传的 PDF 文件 ID")
            String fileId,
            @ToolParam(name = "level", description = "压缩等级 1-5，默认 3")
            Integer level) {

        if (level == null) level = 3;
        if (level < 1 || level > 5) {
            return "错误: 压缩等级必须在 1-5 之间，当前值 " + level;
        }

        log.info("[PdfSkill#pdfCompress] level={}", level);

        byte[] pdfBytes = ctx.loadFile(fileId);
        String baseName = ToolkitContext.extractBaseName(fileId);
        PdfCompressResult result = pdfCompressService.compress(pdfBytes, baseName + ".pdf", level);

        String resultId = ctx.storeFile(result.getData(), baseName + "_compressed.pdf");
        ctx.putResult(ctx.getCurrentConversationId(),
                new ToolkitContext.ToolResult(resultId, baseName + "_compressed.pdf", result.getData().length));
        return String.format("压缩完成！%.1fMB → %.1fMB (%.0f%%), 文件 ID: %s",
                result.getOriginalSize() / (1024.0 * 1024.0),
                result.getCompressedSize() / (1024.0 * 1024.0),
                result.getCompressionRatio() * 100, resultId);
    }

    @Tool(name = "pdfToImage", description = "将 PDF 页面转换为图片")
    public String pdfToImage(
            @ToolParam(name = "fileId", required = true, description = "上传的 PDF 文件 ID")
            String fileId,
            @ToolParam(name = "format", description = "输出格式: png / jpeg / webp，默认 png")
            String format,
            @ToolParam(name = "dpi", description = "DPI 72-600，默认 150")
            Integer dpi,
            @ToolParam(name = "quality", description = "JPEG 质量 0.0-1.0，默认 0.9")
            Float quality,
            @ToolParam(name = "pageRange", description = "页码范围，如 '1-5'，不传=全部")
            String pageRange,
            @ToolParam(name = "trimMargin", description = "是否裁剪白色边框，默认 false")
            Boolean trimMargin) {

        if (format == null || format.isBlank()) format = "png";
        if (dpi == null) dpi = 150;
        if (quality == null) quality = 0.9f;
        if (trimMargin == null) trimMargin = false;
        if (dpi < 72 || dpi > 600) return "错误: DPI 必须在 72-600 之间";

        log.info("[PdfSkill#pdfToImage] format={}, dpi={}, quality={}, pageRange={}, trimMargin={}",
                format, dpi, quality, pageRange, trimMargin);

        byte[] pdfBytes = ctx.loadFile(fileId);
        String baseName = ToolkitContext.extractBaseName(fileId);
        PdfToImageResult result = pdfToImageService.convertToImages(
                pdfBytes, baseName + ".pdf", dpi, format, quality,
                (pageRange != null && !pageRange.isBlank()) ? pageRange : null,
                trimMargin);

        String resultFileName = result.getDownloadFilename();
        String resultId = ctx.storeFile(result.getData(), resultFileName);
        ctx.putResult(ctx.getCurrentConversationId(),
                new ToolkitContext.ToolResult(resultId, resultFileName, result.getData().length));
        return String.format("转换完成！格式: %s, DPI: %d, 裁剪白边: %s, 文件 ID: %s, 大小: %.1fMB",
                format.toUpperCase(), dpi, trimMargin ? "是" : "否",
                resultId, result.getData().length / (1024.0 * 1024.0));
    }

    @Tool(name = "pdfInfo", description = "查询 PDF 文件的页数和每页尺寸。用于编排前确认每个文件的页码范围")
    public String pdfInfo(
            @ToolParam(name = "fileId", required = true, description = "上传的 PDF 文件 ID")
            String fileId) {

        log.info("[PdfSkill#pdfInfo] querying fileId={}", fileId);
        byte[] pdfBytes = ctx.loadFile(fileId);

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pages = doc.getNumberOfPages();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("文件: %s, 总页数: %d\n", fileId, pages));
            for (int i = 0; i < pages; i++) {
                PDPage p = doc.getPage(i);
                PDRectangle box = p.getMediaBox();
                int rot = p.getRotation();
                sb.append(String.format("  第%d页: %.0f×%.0f pt, 旋转=%d°\n",
                        i + 1, box.getWidth(), box.getHeight(), rot));
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("[PdfSkill#pdfInfo] failed: fileId={}", fileId, e);
            return "错误: 无法读取该 PDF 文件的信息，文件可能已损坏或加密。";
        }
    }

    @Tool(name = "pdfArrange", description = "PDF 页面编排：从多个 PDF 中选取指定页面，重新排序并可旋转，最终合并为一个新 PDF")
    public String pdfArrange(
            @ToolParam(name = "fileIds", required = true, description = "源文件 ID 列表，逗号分隔")
            String fileIds,
            @ToolParam(name = "plan", required = true, description = "编排计划 JSON 数组")
            String plan) {

        String[] ids = fileIds.split(",");
        if (ids.length < 1 || ids.length > 10) {
            return "错误: 需要 1-10 个 PDF 文件，当前 " + ids.length + " 个";
        }

        log.info("[PdfSkill#pdfArrange] {} files", ids.length);

        List<byte[]> pdfBytesList = new ArrayList<>();
        List<Integer> pageCounts = new ArrayList<>();
        for (String id : ids) {
            byte[] bytes = ctx.loadFile(id.trim());
            pdfBytesList.add(bytes);
            pageCounts.add(estimatePageCount(bytes));
        }

        List<PdfArrangeItem> planItems;
        try {
            planItems = objectMapper.readValue(plan, new TypeReference<List<PdfArrangeItem>>() {});
        } catch (Exception e) {
            return "错误: plan JSON 格式不正确，请检查语法。示例: "
                    + "[{\"file\":0,\"page\":1},{\"file\":1,\"page\":2}]";
        }

        try {
            byte[] result = pdfArrangeService.arrange(pdfBytesList, planItems);
            String resultId = ctx.storeFile(result, "arranged.pdf");
            ctx.putResult(ctx.getCurrentConversationId(),
                    new ToolkitContext.ToolResult(resultId, "arranged.pdf", result.length));
            return String.format("编排完成！%d 个源文件 → %d 页 PDF, 文件 ID: %s, 大小: %.1fMB",
                    ids.length, planItems.size(), resultId, result.length / (1024.0 * 1024.0));
        } catch (BusinessException e) {
            StringBuilder sb = new StringBuilder();
            sb.append("编排失败: ").append(e.getMessage()).append("\n\n");
            sb.append("各文件页数参考:\n");
            for (int i = 0; i < ids.length; i++) {
                sb.append("  file=").append(i)
                        .append(" (").append(ids[i].trim()).append(")")
                        .append(": ").append(pageCounts.get(i)).append(" 页\n");
            }
            sb.append("\n请根据以上各文件实际页数修正 plan 中的 page 值后重试。");
            return sb.toString();
        }
    }

    @Tool(name = "pdfEncrypt", description = "对 PDF 文件设置密码和权限保护")
    public String pdfEncrypt(
            @ToolParam(name = "fileId", required = true, description = "上传的 PDF 文件 ID")
            String fileId,
            @ToolParam(name = "userPassword", description = "用户密码（打开密码），≥6位含数字和字母")
            String userPassword,
            @ToolParam(name = "ownerPassword", description = "所有者密码（权限密码），≥6位含数字和字母")
            String ownerPassword,
            @ToolParam(name = "canPrint", description = "允许打印，默认 true")
            Boolean canPrint,
            @ToolParam(name = "canCopy", description = "允许复制/提取内容，默认 true")
            Boolean canCopy,
            @ToolParam(name = "canModify", description = "允许修改文档内容，默认 true")
            Boolean canModify,
            @ToolParam(name = "canAnnotate", description = "允许编辑注释和填写表单，默认 true")
            Boolean canAnnotate,
            @ToolParam(name = "canAssemble", description = "允许页面组装，默认 true")
            Boolean canAssemble) {

        if (canPrint == null) canPrint = true;
        if (canCopy == null) canCopy = true;
        if (canModify == null) canModify = true;
        if (canAnnotate == null) canAnnotate = true;
        if (canAssemble == null) canAssemble = true;

        log.info("[PdfSkill#pdfEncrypt] fileId={}, userPwd={}, ownerPwd={}",
                fileId, mask(userPassword), mask(ownerPassword));

        byte[] pdfBytes = ctx.loadFile(fileId);
        String baseName = ToolkitContext.extractBaseName(fileId);

        try {
            byte[] result = pdfEncryptService.encrypt(
                    pdfBytes, userPassword, ownerPassword,
                    canPrint, canCopy, canModify, canAnnotate, canAssemble);

            String resultId = ctx.storeFile(result, baseName + "_encrypted.pdf");
            ctx.putResult(ctx.getCurrentConversationId(),
                    new ToolkitContext.ToolResult(resultId, baseName + "_encrypted.pdf", result.length));
            return String.format("加密完成！文件 ID: %s, 大小: %.1fMB",
                    resultId, result.length / (1024.0 * 1024.0));
        } catch (BusinessException e) {
            return "加密失败: " + e.getMessage() + "\n\n"
                    + "密码要求：≥6位，需包含数字和字母\n"
                    + "约束：两个密码不能相同，至少填写一个，所有者密码存在时至少关闭一项权限";
        } catch (Exception e) {
            log.error("[PdfSkill#pdfEncrypt] failed", e);
            return "PDF 加密失败，请稍后重试。";
        }
    }

    // ===== PDF 水印 / 去水印 / 涂黑 =====

    @Tool(name = "pdfWatermark", description = "给 PDF 添加水印。source=text 为文字水印，source=image 为图片水印（需上传图片）")
    public String pdfWatermark(
            @ToolParam(name = "fileId", required = true, description = "上传的 PDF 文件 ID")
            String fileId,
            @ToolParam(name = "source", description = "水印来源: text(默认) / image")
            String source,
            @ToolParam(name = "text", description = "水印文字（文字水印必填），如 '内部资料'")
            String text,
            @ToolParam(name = "fontSize", description = "字号(pt)，默认 28")
            Integer fontSize,
            @ToolParam(name = "color", description = "颜色 hex，如 '#808080'")
            String color,
            @ToolParam(name = "angle", description = "旋转角度(度)，默认 0")
            Double angle,
            @ToolParam(name = "opacity", description = "透明度 0-1，默认 0.5")
            Double opacity,
            @ToolParam(name = "ratio", description = "图片水印宽度占页面百分比 5-100，默认 50")
            Integer ratio,
            @ToolParam(name = "fixedRatio", description = "固定水印比例（不随页面缩放），默认 false")
            Boolean fixedRatio,
            @ToolParam(name = "alignX", description = "水平对齐 left/center/right，默认 center")
            String alignX,
            @ToolParam(name = "alignY", description = "垂直对齐 top/middle/bottom，默认 middle")
            String alignY,
            @ToolParam(name = "offsetX", description = "水平偏移(cm)，默认 0")
            Double offsetX,
            @ToolParam(name = "offsetY", description = "垂直偏移(cm)，默认 0")
            Double offsetY,
            @ToolParam(name = "range", description = "应用范围 all(默认)/pageRange")
            String range,
            @ToolParam(name = "fromPage", description = "起始页(1-based, range=pageRange 时)")
            Integer fromPage,
            @ToolParam(name = "toPage", description = "结束页(range=pageRange 时)")
            Integer toPage,
            @ToolParam(name = "subset", description = "子集 all/odd/even，默认 all")
            String subset,
            @ToolParam(name = "imageFileId", description = "图片水印的图片文件 ID（source=image 时）")
            String imageFileId) {

        try {
            byte[] pdfBytes = ctx.loadFile(fileId);
            String baseName = ToolkitContext.extractBaseName(fileId);

            WatermarkRequest req = new WatermarkRequest();
            req.setSource(source != null ? source : "text");
            req.setText(text);
            if (fontSize != null) req.setFontSize(fontSize.floatValue());
            if (color != null) req.setColor(color);
            if (angle != null) req.setAngle(angle);
            if (opacity != null) req.setOpacity(opacity);
            if (ratio != null) req.setRatio(ratio.doubleValue());
            if (fixedRatio != null) req.setFixedRatio(fixedRatio);
            if (alignX != null) req.setAlignX(alignX);
            if (alignY != null) req.setAlignY(alignY);
            if (offsetX != null) req.setOffsetX(offsetX);
            if (offsetY != null) req.setOffsetY(offsetY);
            if (range != null) req.setRange(range);
            if (fromPage != null) req.setFromPage(fromPage);
            if (toPage != null) req.setToPage(toPage);
            if (subset != null) req.setSubset(subset);

            byte[] imageBytes = (imageFileId != null && !imageFileId.isBlank())
                    ? ctx.loadFile(imageFileId) : null;
            byte[] result = pdfWatermarkService.addWatermark(pdfBytes, baseName + ".pdf", req, imageBytes);

            String resultId = ctx.storeFile(result, baseName + "_watermarked.pdf");
            ctx.putResult(ctx.getCurrentConversationId(),
                    new ToolkitContext.ToolResult(resultId, baseName + "_watermarked.pdf", result.length));
            return String.format("水印添加完成！文件 ID: %s, 大小: %.1fMB",
                    resultId, result.length / (1024.0 * 1024.0));
        } catch (BusinessException e) {
            return "加水印失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("[PdfSkill#pdfWatermark] failed", e);
            return "PDF 添加水印失败，请稍后重试。";
        }
    }

    @Tool(name = "pdfDewatermark", description = "去除 PDF 水印。按水印大致位置(position)删除对应区域文字/图片水印，保留正文")
    public String pdfDewatermark(
            @ToolParam(name = "fileId", required = true, description = "上传的 PDF 文件 ID")
            String fileId,
            @ToolParam(name = "position", required = true, description = "水印位置: center(居中)/top(上方)/bottom(下方)/left(左侧)/right(右侧)/tl(左上)/tr(右上)/bl(左下)/br(右下)")
            String position,
            @ToolParam(name = "applyTo", description = "应用范围 all(默认所有页)/page(仅指定页)")
            String applyTo,
            @ToolParam(name = "page", description = "applyTo=page 时指定页(1-based)")
            Integer page) {

        try {
            byte[] pdfBytes = ctx.loadFile(fileId);
            String baseName = ToolkitContext.extractBaseName(fileId);

            DewatermarkRequest req = new DewatermarkRequest();
            req.setApplyTo("page".equals(applyTo) && page != null ? "page" : "all");
            req.setRegions(buildDewatermarkRegions(pdfBytes, position, page));

            DewatermarkResult result = pdfDewatermarkService.dewatermark(pdfBytes, baseName + ".pdf", req);
            byte[] out = java.util.Base64.getDecoder().decode(result.getPdfBase64());

            String resultId = ctx.storeFile(out, baseName + "_dewatermarked.pdf");
            ctx.putResult(ctx.getCurrentConversationId(),
                    new ToolkitContext.ToolResult(resultId, baseName + "_dewatermarked.pdf", out.length));
            String failed = result.getFailed() == null || result.getFailed().isEmpty() ? "" :
                    String.format("，%d 个区域未能自动去除", result.getFailed().size());
            return String.format("去水印完成！已去除 %d 个区域%s, 文件 ID: %s, 大小: %.1fMB",
                    result.getRemoved() == null ? 0 : result.getRemoved().size(),
                    failed, resultId, out.length / (1024.0 * 1024.0));
        } catch (BusinessException e) {
            return "去水印失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("[PdfSkill#pdfDewatermark] failed", e);
            return "PDF 去水印失败，请稍后重试。";
        }
    }

    @Tool(name = "pdfRedact", description = "PDF 涂黑遮盖。在指定位置绘制方块遮盖敏感内容，mode=deep 时彻底清除该区域底层内容")
    public String pdfRedact(
            @ToolParam(name = "fileId", required = true, description = "上传的 PDF 文件 ID")
            String fileId,
            @ToolParam(name = "position", required = true, description = "遮盖位置: center/top/bottom/left/right/tl/tr/bl/br")
            String position,
            @ToolParam(name = "mode", description = "遮盖模式 standard(覆盖)/deep(彻底清除)，默认 standard")
            String mode,
            @ToolParam(name = "applyTo", description = "应用范围 all(默认所有页)/page(仅指定页)")
            String applyTo,
            @ToolParam(name = "page", description = "applyTo=page 时指定页(1-based)")
            Integer page) {

        try {
            byte[] pdfBytes = ctx.loadFile(fileId);
            String baseName = ToolkitContext.extractBaseName(fileId);

            RedactRequest req = new RedactRequest();
            req.setMode(mode != null ? mode : "standard");
            req.setRects(buildRedactRects(pdfBytes, position, page,
                    "page".equals(applyTo) && page != null));

            byte[] result = pdfRedactService.redact(pdfBytes, baseName + ".pdf", req);

            String resultId = ctx.storeFile(result, baseName + "_redacted.pdf");
            ctx.putResult(ctx.getCurrentConversationId(),
                    new ToolkitContext.ToolResult(resultId, baseName + "_redacted.pdf", result.length));
            return String.format("涂黑遮盖完成！文件 ID: %s, 大小: %.1fMB",
                    resultId, result.length / (1024.0 * 1024.0));
        } catch (BusinessException e) {
            return "涂黑遮盖失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("[PdfSkill#pdfRedact] failed", e);
            return "PDF 涂黑遮盖失败，请稍后重试。";
        }
    }

    // ===== 辅助方法 =====

    /**
     * 根据位置预设构建去水印区域（前端左上角坐标系，points）
     */
    private List<DewatermarkRequest.RegionItem> buildDewatermarkRegions(byte[] pdfBytes, String position, Integer page) {
        List<DewatermarkRequest.RegionItem> regions = new ArrayList<>();
        int targetPage = (page != null && page >= 1) ? page - 1 : 0;
        float[] wh = readPageSize(pdfBytes, targetPage);
        if (wh == null) wh = new float[]{612, 792};
        float W = wh[0], H = wh[1];
        float[] box = positionBox(position, W, H);
        regions.add(new DewatermarkRequest.RegionItem(targetPage, box[0], box[1], box[2], box[3]));
        return regions;
    }

    /**
     * 根据位置预设构建涂黑矩形（前端左上角坐标系）
     */
    private List<RedactRequest.RectItem> buildRedactRects(byte[] pdfBytes, String position, Integer page, boolean singlePage) {
        List<RedactRequest.RectItem> rects = new ArrayList<>();
        int targetPage = (page != null && page >= 1) ? page - 1 : 0;
        float[] wh = readPageSize(pdfBytes, targetPage);
        if (wh == null) wh = new float[]{612, 792};
        float W = wh[0], H = wh[1];
        float[] box = positionBox(position, W, H);
        rects.add(new RedactRequest.RectItem(targetPage, box[0], box[1], box[2], box[3], "#000000"));
        return rects;
    }

    /**
     * 位置预设 → 前端左上角区域 [x, y, w, h]（页面分数 → points）
     */
    private static float[] positionBox(String pos, float W, float H) {
        String p = pos == null ? "center" : pos.trim().toLowerCase();
        float x, y, w, h;
        switch (p) {
            case "top" -> { x = 0; y = 0; w = W; h = H * 0.25f; }
            case "bottom" -> { x = 0; y = H * 0.75f; w = W; h = H * 0.25f; }
            case "left" -> { x = 0; y = 0; w = W * 0.25f; h = H; }
            case "right" -> { x = W * 0.75f; y = 0; w = W * 0.25f; h = H; }
            case "tl" -> { x = 0; y = 0; w = W * 0.3f; h = H * 0.3f; }
            case "tr" -> { x = W * 0.7f; y = 0; w = W * 0.3f; h = H * 0.3f; }
            case "bl" -> { x = 0; y = H * 0.7f; w = W * 0.3f; h = H * 0.3f; }
            case "br" -> { x = W * 0.7f; y = H * 0.7f; w = W * 0.3f; h = H * 0.3f; }
            default -> { x = W * 0.25f; y = H * 0.25f; w = W * 0.5f; h = H * 0.5f; } // center
        }
        return new float[]{x, y, w, h};
    }

    /**
     * 读取某页的宽高（points）
     */
    private static float[] readPageSize(byte[] pdfBytes, int pageIndex) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            if (pageIndex < 0 || pageIndex >= doc.getNumberOfPages()) return null;
            PDRectangle box = doc.getPage(pageIndex).getMediaBox();
            return new float[]{box.getWidth(), box.getHeight()};
        } catch (Exception e) {
            return null;
        }
    }

    private int estimatePageCount(byte[] pdfBytes) {
        int count = 0;
        int limit = Math.min(pdfBytes.length, 1024 * 1024);
        for (int i = 0; i < limit - 10; i++) {
            if (pdfBytes[i] == '/' && pdfBytes[i + 1] == 'T' && pdfBytes[i + 2] == 'y'
                    && pdfBytes[i + 3] == 'p' && pdfBytes[i + 4] == 'e'
                    && pdfBytes[i + 5] == ' ' && pdfBytes[i + 6] == '/'
                    && pdfBytes[i + 7] == 'P' && pdfBytes[i + 8] == 'a'
                    && pdfBytes[i + 9] == 'g' && pdfBytes[i + 10] == 'e') {
                count++;
                i += 10;
            }
        }
        return Math.max(count, 1);
    }

    private static String mask(String s) {
        if (s == null || s.isBlank()) return "(空)";
        return "*".repeat(s.length());
    }
}
