package com.toolbox.service.agent.skill;

import com.toolbox.exception.BusinessException;
import com.toolbox.service.image.ImageToPdfService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Image Skill — 图片转 PDF 工具组
 * <p>
 * 工具列表：imageToPdf
 *
 * @author toolbox
 * @since 2026-07-22
 */
public class ImageSkill implements AgentSkill {

    private static final Logger log = LoggerFactory.getLogger(ImageSkill.class);

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final ToolkitContext ctx;
    private final ImageToPdfService imageToPdfService;

    public ImageSkill(ToolkitContext ctx, ImageToPdfService imageToPdfService) {
        this.ctx = ctx;
        this.imageToPdfService = imageToPdfService;
    }

    // ===== AgentSkill 接口 =====

    @Override
    public String name() { return "image"; }

    @Override
    public String description() { return "图片转 PDF（JPG/PNG/WEBP/GIF）"; }

    @Override
    public List<String> keywords() {
        return List.of("图片转PDF", "图片合并", "图片转", "转PDF", "JPG转", "PNG转", "图片转成");
    }

    @Override
    public List<String> fileExtensions() {
        return List.of(".jpg", ".jpeg", ".png", ".webp", ".gif");
    }

    @Override
    public List<Object> toolInstances() { return List.of(this); }

    @Override
    public String promptFragment() { return ToolkitContext.loadPrompt("prompts/skill-image.md"); }

    // ===== @Tool 方法 =====

    @Tool(name = "imageToPdf", description = "将多张图片合并为 PDF 文件。支持 JPG/PNG/WEBP/GIF 格式")
    public String imageToPdf(
            @ToolParam(name = "fileIds", required = true, description = "图片文件 ID 列表，逗号分隔")
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

        log.info("[ImageSkill#imageToPdf] {} images, orientation={}, margin={}, fitMode={}, merge={}",
                ids.length, orientation, margin, fitMode, merge);

        List<byte[]> imageBytesList = new ArrayList<>();
        List<String> extensions = new ArrayList<>();
        for (String id : ids) {
            String trimmedId = id.trim();
            byte[] bytes = ctx.loadFile(trimmedId);
            imageBytesList.add(bytes);

            // 提取扩展名，如果 fileId 没有扩展名则从文件头检测
            String ext = trimmedId.contains(".")
                    ? trimmedId.substring(trimmedId.lastIndexOf('.')).toLowerCase()
                    : detectImageExtension(bytes);
            if (ext.isEmpty() || !ALLOWED_IMAGE_EXTENSIONS.contains(ext.replace(".", ""))) {
                return "错误: 不支持的图片格式: " + ext + "，仅支持 JPG/PNG/WEBP/GIF";
            }
            extensions.add(ext.startsWith(".") ? ext : "." + ext);
        }

        try {
            byte[] result;
            String resultFileName;

            if (merge) {
                result = imageToPdfService.convertToPdf(
                        imageBytesList, extensions, orientation, margin, fitMode);
                resultFileName = "images.pdf";
            } else {
                result = buildZipOfPdfs(imageBytesList, extensions, orientation, margin, fitMode, ids);
                resultFileName = "images.zip";
            }

            String resultId = ctx.storeFile(result, resultFileName);
            ctx.putResult(ctx.getCurrentConversationId(),
                    new ToolkitContext.ToolResult(resultId, resultFileName, result.length));
            return String.format("转换完成！%d 张图片 → %s, 文件 ID: %s, 大小: %.1fMB",
                    ids.length, merge ? "单个 PDF" : "ZIP 包",
                    resultId, result.length / (1024.0 * 1024.0));
        } catch (BusinessException e) {
            return "转换失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("[ImageSkill#imageToPdf] conversion failed", e);
            return "图片转 PDF 失败，请稍后重试。";
        }
    }

    /**
     * 从文件头检测图片格式
     */
    private static String detectImageExtension(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return "";
        // PNG: 89 50 4E 47
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) return ".png";
        // JPEG: FF D8 FF
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) return ".jpg";
        // GIF: 47 49 46
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) return ".gif";
        // WEBP: 52 49 46 46 ... 57 45 42 50
        if (bytes.length >= 12 && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) return ".webp";
        return "";
    }

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
