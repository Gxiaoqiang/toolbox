package com.toolbox.service.agent;

import com.toolbox.exception.BusinessException;
import com.toolbox.service.document.DocumentService;
import com.toolbox.service.image.ImageToPdfService;
import com.toolbox.service.markdown.MarkdownService;
import com.toolbox.service.pdf.*;
import com.toolbox.service.store.FileStore;
import com.toolbox.model.common.PdfArrangeItem;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    private final FileStore fileStore;

    private final PdfArrangeService pdfArrangeService;
    private final ImageToPdfService imageToPdfService;
    private final ObjectMapper objectMapper;

    /** 当前正在处理的对话 ID — AgentServiceImpl 调用前设置 */
    private volatile String currentConversationId;

    /** 各对话的工具产物 — 按 conversationId 隔离，支持并发 */
    private final ConcurrentHashMap<String, ToolResult> conversationResults = new ConcurrentHashMap<>();

    public DocAgentToolkit(PdfService pdfService, PdfCompressService pdfCompressService,
                           PdfToImageService pdfToImageService,
                           DocumentService documentService, MarkdownService markdownService,
                           FileStore fileStore, PdfArrangeService pdfArrangeService,
                           ImageToPdfService imageToPdfService,
                           ObjectMapper objectMapper) {
        this.pdfService = pdfService;
        this.pdfCompressService = pdfCompressService;
        this.pdfToImageService = pdfToImageService;
        this.documentService = documentService;
        this.markdownService = markdownService;
        this.fileStore = fileStore;
        this.pdfArrangeService = pdfArrangeService;
        this.imageToPdfService = imageToPdfService;
        this.objectMapper = objectMapper;
    }

    /**
     * 设置当前对话 ID — AgentServiceImpl 在调用 agent 前设置
     */
    public void setCurrentConversationId(String conversationId) {
        this.currentConversationId = conversationId;
    }

    /**
     * 获取指定对话的工具产物（一次性消费）
     *
     * @param conversationId 对话 ID
     * @return 工具产物，无产物返回 null
     */
    public ToolResult getLastResult(String conversationId) {
        return conversationResults.remove(conversationId);
    }

    /**
     * 清理指定对话的残留产物 — finally 块中调用，防止内存泄漏
     */
    public void clearResult(String conversationId) {
        conversationResults.remove(conversationId);
    }

    /** 工具产物记录 */
    public record
    ToolResult(String fileId, String fileName, long size) {}

    // ===== 辅助方法 =====

    private byte[] loadFile(String fileId) {
        log.info("[DocAgentToolkit#loadFile] loading fileId={}", fileId);
        try {
            byte[] data = fileStore.load(fileId);
            log.info("[DocAgentToolkit#loadFile] loaded {} bytes from {}", data.length, fileId);
            return data;
        } catch (Exception e) {
            log.error("[DocAgentToolkit#loadFile] file not found: fileId={}", fileId, e);
            throw new RuntimeException("文件不存在或已过期: " + fileId, e);
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

        String resultId = fileStore.store(result, baseName + "_split.zip");
        conversationResults.put(currentConversationId, new ToolResult(resultId, baseName + "_split.zip", result.length));
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
        String resultId = fileStore.store(result, "merged.pdf");
        conversationResults.put(currentConversationId, new ToolResult(resultId, "merged.pdf", result.length));
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

        String resultId = fileStore.store(result.getData(), baseName + "_compressed.pdf");
        conversationResults.put(currentConversationId, new ToolResult(resultId, baseName + "_compressed.pdf", result.getData().length));
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

        // 单页用 PdfToImageResult 返回的正确文件名（如 xxx.png），多页才是 .zip
        String resultFileName = result.getDownloadFilename();
        String resultId = fileStore.store(result.getData(), resultFileName);
        conversationResults.put(currentConversationId, new ToolResult(resultId, resultFileName, result.getData().length));
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
        // 保留原始扩展名！LibreOffice 靠扩展名识别格式：.docx ≠ .doc
        String ext = fileId.contains(".") ? fileId.substring(fileId.lastIndexOf('.')) : ".docx";
        try {
            byte[] result = documentService.convertToPdf(docBytes, baseName + ext);

            String resultId = fileStore.store(result, baseName + ".pdf");
            conversationResults.put(currentConversationId, new ToolResult(resultId, baseName + ".pdf", result.length));
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
        String resultId = fileStore.store(result, outputName + ".docx");
        conversationResults.put(currentConversationId, new ToolResult(resultId, outputName + ".docx", result.length));
        return String.format("转换完成！%d 字符 → DOCX, 文件 ID: %s, 大小: %.1fKB",
                markdownContent.length(), resultId, result.length / 1024.0);
    }

    @Tool(name = "pdfInfo", description = "查询 PDF 文件的页数和每页尺寸。用于编排前确认每个文件的页码范围，生成编排 plan 时引用")
    public String pdfInfo(
            @ToolParam(name = "fileId", required = true, description = "上传的 PDF 文件 ID")
            String fileId) {

        log.info("[DocAgentToolkit#pdfInfo] querying fileId={}", fileId);

        byte[] pdfBytes = loadFile(fileId);

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
            log.error("[DocAgentToolkit#pdfInfo] failed to query pdf info: fileId={}", fileId, e);
            return "错误: 无法读取该 PDF 文件的信息，文件可能已损坏或加密。";
        }
    }

    @Tool(name = "pdfArrange",
            description = "PDF 页面编排：从多个 PDF 中选取指定页面，重新排序并可旋转，最终合并为一个新 PDF。先调用 pdfInfo 获取每文件页数，再生成 plan")
    public String pdfArrange(
            @ToolParam(name = "fileIds", required = true,
                    description = "源文件 ID 列表，逗号分隔，如 'abc.pdf,def.pdf'。plan 中的 file = 此数组下标（0-based）")
            String fileIds,
            @ToolParam(name = "plan", required = true,
                    description = "编排计划 JSON 数组。每项: {\"file\":文件下标,\"page\":页码}\n"
                            + "旋转: 加 \"rotate\":90/180/270\n"
                            + "空白页: {\"blank\":true}\n"
                            + "空白页尺寸(可选): {\"blank\":true,\"width\":595,\"height\":842}\n"
                            + "复制页 = 同一 {\"file\",\"page\"} 出现两次\n"
                            + "示例: [{\"file\":0,\"page\":1},{\"file\":0,\"page\":3,\"rotate\":90},{\"blank\":true},{\"file\":1,\"page\":2}]")
            String plan) {

        String[] ids = fileIds.split(",");
        if (ids.length < 1 || ids.length > 10) {
            return "错误: 需要 1-10 个 PDF 文件，当前 " + ids.length + " 个";
        }

        log.info("[DocAgentToolkit#pdfArrange] {} files", ids.length);

        List<byte[]> pdfBytesList = new ArrayList<>();
        List<Integer> pageCounts = new ArrayList<>();
        for (String id : ids) {
            byte[] bytes = loadFile(id.trim());
            pdfBytesList.add(bytes);
            pageCounts.add(estimatePageCount(bytes));
        }

        List<PdfArrangeItem> planItems;
        try {
            planItems = objectMapper.readValue(plan,
                    new TypeReference<List<PdfArrangeItem>>() {});
        } catch (Exception e) {
            return "错误: plan JSON 格式不正确，请检查语法。示例: "
                    + "[{\"file\":0,\"page\":1},{\"file\":1,\"page\":2}]";
        }

        try {
            byte[] result = pdfArrangeService.arrange(pdfBytesList, planItems);

            String resultId = fileStore.store(result, "arranged.pdf");
            conversationResults.put(currentConversationId, new ToolResult(resultId, "arranged.pdf", result.length));
            return String.format("编排完成！%d 个源文件 → %d 页 PDF, 文件 ID: %s, 大小: %.1fMB",
                    ids.length, planItems.size(), resultId,
                    result.length / (1024.0 * 1024.0));

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

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @Tool(name = "imageToPdf", description = "将多张图片合并为 PDF 文件。支持 JPG/PNG/WEBP/GIF 格式，可配置页面方向、边距和适配方式")
    public String imageToPdf(
            @ToolParam(name = "fileIds", required = true, description = "图片文件 ID 列表，逗号分隔，如 'abc.jpg,def.png'")
            String fileIds,
            @ToolParam(name = "orientation", description = "页面方向: portrait(纵向)/landscape(横向)，默认 portrait")
            String orientation,
            @ToolParam(name = "margin", description = "页面边距: none(无)/small(小)/large(大)，默认 small")
            String margin,
            @ToolParam(name = "fitMode", description = "图片适配方式: contain(等比)/cover(裁剪)/stretch(拉伸)，默认 contain")
            String fitMode,
            @ToolParam(name = "merge", description = "是否合并为一个 PDF: true(合并)/false(独立打包ZIP)，默认 true")
            Boolean merge) {

        if (orientation == null || orientation.isBlank()) orientation = "portrait";
        if (margin == null || margin.isBlank()) margin = "small";
        if (fitMode == null || fitMode.isBlank()) fitMode = "contain";
        if (merge == null) merge = true;

        String[] ids = fileIds.split(",");
        if (ids.length < 1 || ids.length > 50) {
            return "错误: 需要 1-50 张图片，当前 " + ids.length + " 个";
        }

        log.info("[DocAgentToolkit#imageToPdf] {} images, orientation={}, margin={}, fitMode={}, merge={}",
                ids.length, orientation, margin, fitMode, merge);

        // 1. 加载所有图片文件
        List<byte[]> imageBytesList = new ArrayList<>();
        List<String> extensions = new ArrayList<>();
        for (String id : ids) {
            String trimmedId = id.trim();
            byte[] bytes = loadFile(trimmedId);
            imageBytesList.add(bytes);

            // 提取扩展名
            String ext = trimmedId.contains(".")
                    ? trimmedId.substring(trimmedId.lastIndexOf('.')).toLowerCase()
                    : "";
            if (!ALLOWED_IMAGE_EXTENSIONS.contains(ext.replace(".", ""))) {
                return "错误: 不支持的图片格式: " + ext + "，仅支持 JPG/PNG/WEBP/GIF";
            }
            extensions.add(ext);
        }

        // 2. 执行转换
        try {
            byte[] result;
            String resultFileName;

            if (merge) {
                // 合并模式
                result = imageToPdfService.convertToPdf(
                        imageBytesList, extensions, orientation, margin, fitMode);
                resultFileName = "images.pdf";
            } else {
                // 独立模式：每张图片独立转 PDF 后打包 ZIP
                result = buildZipOfPdfs(imageBytesList, extensions, orientation, margin, fitMode, ids);
                resultFileName = "images.zip";
            }

            String resultId = fileStore.store(result, resultFileName);
            conversationResults.put(currentConversationId,
                    new ToolResult(resultId, resultFileName, result.length));
            return String.format("转换完成！%d 张图片 → %s, 文件 ID: %s, 大小: %.1fMB",
                    ids.length, merge ? "单个 PDF" : "ZIP 包",
                    resultId, result.length / (1024.0 * 1024.0));

        } catch (BusinessException e) {
            return "转换失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("[DocAgentToolkit#imageToPdf] conversion failed", e);
            return "图片转 PDF 失败，请稍后重试。";
        }
    }

    /**
     * 每张图片独立转 PDF 后打包为 ZIP
     */
    private byte[] buildZipOfPdfs(List<byte[]> imageBytesList, List<String> extensions,
                                   String orientation, String margin, String fitMode,
                                   String[] ids) throws Exception {
        java.io.ByteArrayOutputStream zipOut = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
            for (int i = 0; i < imageBytesList.size(); i++) {
                byte[] pdfBytes = imageToPdfService.convertToPdf(
                        List.of(imageBytesList.get(i)),
                        List.of(extensions.get(i)),
                        orientation, margin, fitMode);

                String baseName = ids[i].trim();
                int dot = baseName.lastIndexOf('.');
                String name = dot > 0 ? baseName.substring(0, dot) : baseName;

                zos.putNextEntry(new ZipEntry(name + ".pdf"));
                zos.write(pdfBytes);
                zos.closeEntry();
            }
        }
        return zipOut.toByteArray();
    }
}
