package com.toolbox.service.agent;

import com.toolbox.service.document.DocumentService;
import com.toolbox.service.markdown.MarkdownService;
import com.toolbox.service.pdf.*;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 工具箱 — 6 个 @Tool 方法封装现有文档处理 Service
 * <p>
 * 使用 AgentScope 的 @Tool 注解标记方法，Toolkit.registerTool(this) 注册。
 * 每个方法内部委托现有 Service 处理，只做参数补全 + 结果格式化。
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class DocAgentToolkit {

    private static final Logger log = LoggerFactory.getLogger(DocAgentToolkit.class);

    private final PdfService pdfService;
    private final PdfCompressService pdfCompressService;
    private final PdfToImageService pdfToImageService;
    private final DocumentService documentService;
    private final MarkdownService markdownService;
    private final FileManager fileManager;

    /** 最后一次工具调用的产物信息 */
    private volatile ToolResult lastResult;

    public DocAgentToolkit(PdfService pdfService, PdfCompressService pdfCompressService,
                           PdfToImageService pdfToImageService,
                           DocumentService documentService, MarkdownService markdownService,
                           FileManager fileManager) {
        this.pdfService = pdfService;
        this.pdfCompressService = pdfCompressService;
        this.pdfToImageService = pdfToImageService;
        this.documentService = documentService;
        this.markdownService = markdownService;
        this.fileManager = fileManager;
    }

    /** 获取最后一次工具调用的产物（AgentService 用于推送 result SSE 事件） */
    public ToolResult getLastResult() {
        ToolResult r = lastResult;
        lastResult = null; // 一次性消费
        return r;
    }

    /** 工具产物记录 */
    public record ToolResult(String fileId, String fileName, long size) {}

    // ===== 辅助方法 =====

    private byte[] loadFile(String fileId) {
        File file = fileManager.load(fileId);
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("无法读取文件: " + fileId, e);
        }
    }

    private String extractBaseName(String fileId) {
        if (fileId == null) return "file";
        int dot = fileId.lastIndexOf('.');
        return dot > 0 ? fileId.substring(0, dot) : fileId;
    }

    private int estimatePageCount(byte[] pdfBytes) {
        int count = 0;
        // 搜索 "/Type /Page" 模式（限制搜索前 1MB 避免大文件 OOM）
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

        log.info("[DocAgentToolkit#pdfSplit] mode={}, pages={}, everyN={}", mode, pages, everyN);

        byte[] pdfBytes = loadFile(fileId);
        String baseName = extractBaseName(fileId);
        byte[] result = pdfService.splitPdf(pdfBytes, baseName + ".pdf", mode,
                pages != null ? pages : "", everyN, true);

        String resultId = fileManager.storeBytes(result, baseName + "_split.zip");
        lastResult = new ToolResult(resultId, baseName + "_split.zip", result.length);
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

        log.info("[DocAgentToolkit#pdfMerge] merging {} files", ids.length);

        List<byte[]> bytesList = new ArrayList<>();
        for (String id : ids) {
            bytesList.add(loadFile(id.trim()));
        }

        byte[] result = pdfService.mergePdf(bytesList, true);
        String resultId = fileManager.storeBytes(result, "merged.pdf");
        lastResult = new ToolResult(resultId, "merged.pdf", result.length);
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

        log.info("[DocAgentToolkit#pdfCompress] level={}", level);

        byte[] pdfBytes = loadFile(fileId);
        String baseName = extractBaseName(fileId);
        PdfCompressResult result = pdfCompressService.compress(pdfBytes, baseName + ".pdf", level);

        String resultId = fileManager.storeBytes(result.getData(), baseName + "_compressed.pdf");
        lastResult = new ToolResult(resultId, baseName + "_compressed.pdf", result.getData().length);
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
            String pageRange) {

        if (format == null || format.isBlank()) format = "png";
        if (dpi == null) dpi = 150;
        if (quality == null) quality = 0.9f;
        if (dpi < 72 || dpi > 600) return "错误: DPI 必须在 72-600 之间";

        log.info("[DocAgentToolkit#pdfToImage] format={}, dpi={}, quality={}, pageRange={}",
                format, dpi, quality, pageRange);

        byte[] pdfBytes = loadFile(fileId);
        String baseName = extractBaseName(fileId);
        PdfToImageResult result = pdfToImageService.convertToImages(
                pdfBytes, baseName + ".pdf", dpi, format, quality,
                (pageRange != null && !pageRange.isBlank()) ? pageRange : null);

        String resultId = fileManager.storeBytes(result.getData(), baseName + "_images.zip");
        lastResult = new ToolResult(resultId, baseName + "_images.zip", result.getData().length);
        return String.format("转换完成！格式: %s, DPI: %d, 文件 ID: %s, 大小: %.1fMB",
                format.toUpperCase(), dpi, resultId,
                result.getData().length / (1024.0 * 1024.0));
    }

    @Tool(name = "docToPdf", description = "将 Word(.doc/.docx)或 WPS(.wps)文档转换为 PDF")
    public String docToPdf(
            @ToolParam(name = "fileId", required = true, description = "上传的文档文件 ID")
            String fileId) {

        if (!documentService.isServiceAvailable()) {
            return "错误: 文档转 PDF 服务暂不可用（LibreOffice 未启动），请联系管理员。";
        }

        log.info("[DocAgentToolkit#docToPdf] converting file: {}", fileId);

        byte[] docBytes = loadFile(fileId);
        String baseName = extractBaseName(fileId);
        try {
            byte[] result = documentService.convertToPdf(docBytes, baseName + ".doc");

            String resultId = fileManager.storeBytes(result, baseName + ".pdf");
            lastResult = new ToolResult(resultId, baseName + ".pdf", result.length);
            return String.format("转换完成！文件 ID: %s, 大小: %.1fMB",
                    resultId, result.length / (1024.0 * 1024.0));
        } catch (Exception e) {
            log.error("[DocAgentToolkit#docToPdf] conversion failed", e);
            return "文档转 PDF 失败: LibreOffice 无法转换此文件。"
                    + "请确认文件是有效的 .doc/.docx/.wps 格式，且没有损坏。"
                    + "也可以尝试用 Word 另存为后再上传。";
        }
    }

    @Tool(name = "mdToDocx", description = "将 Markdown 文本转换为 DOCX 文件")
    public String mdToDocx(
            @ToolParam(name = "markdownContent", required = true, description = "Markdown 文本内容")
            String markdownContent,
            @ToolParam(name = "outputName", description = "输出文件名（不含扩展名），默认 output")
            String outputName) {

        if (outputName == null || outputName.isBlank()) outputName = "output";

        log.info("[DocAgentToolkit#mdToDocx] converting markdown, length={}",
                markdownContent.length());

        byte[] result = markdownService.convertMarkdownToDocx(markdownContent);
        String resultId = fileManager.storeBytes(result, outputName + ".docx");
        lastResult = new ToolResult(resultId, outputName + ".docx", result.length);
        return String.format("转换完成！%d 字符 → DOCX, 文件 ID: %s, 大小: %.1fKB",
                markdownContent.length(), resultId, result.length / 1024.0);
    }
}
