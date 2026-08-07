package com.toolbox.service.ocr;

import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 可搜索 PDF 写入器
 * <p>
 * 将 OCR 识别出的文本以透明文字层叠加到原扫描页上，使扫描件可搜索、可复制。
 * 词级包围盒从图片像素坐标转换到 PDF 页面坐标系（左下角为原点，单位 pt）。
 * <p>
 * 关键：标准 14 字体（Helvetica 等）仅支持 Latin，无法编码中文 OCR 结果，
 * 必须嵌入一个支持 CJK 的 TrueType/OpenType 字体（跨平台探测系统字体目录）。
 *
 * @author toolbox
 * @since 2026-08-04
 */
@Component
public class SearchablePdfWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchablePdfWriter.class);

    /** 常见支持中文的字体路径（跨平台探测） */
    private static final List<String> CANDIDATE_FONT_PATHS = List.of(
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",  // Debian/Ubuntu
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",            // Ubuntu WQY
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",  // 部分发行版
            "/System/Library/Fonts/PingFang.ttc",                      // macOS
            "/System/Library/Fonts/STHeiti Light.ttc",                 // macOS
            "/System/Library/Fonts/Supplemental/Songti.ttc");          // macOS

    /** 自定义中文字体路径（可选，优先于系统探测） */
    @Value("${toolbox.ocr.font-path:}")
    private String fontPath;

    /**
     * 在扫描页上叠加透明 OCR 文字层，生成可搜索 PDF
     *
     * @param doc        原始 PDF 文档（会被就地修改）
     * @param ocrResults 每页 OCR 结果，下标与页码一致；原生页为 null（跳过）
     * @param types      每页类型标记
     * @param dpi        渲染 DPI（用于坐标换算）
     * @return 可搜索 PDF 字节数组
     */
    public byte[] build(PDDocument doc, List<OcrResult> ocrResults,
                        List<PdfAnalyzer.PageType> types, int dpi) {
        try {
            PDFont font = loadFont(doc);

            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                if (types.get(i) != PdfAnalyzer.PageType.SCANNED) {
                    continue; // 原生页无需叠加
                }
                OcrResult result = ocrResults.get(i);
                if (result == null || result.wordBoxes().isEmpty()) {
                    continue;
                }
                overlayPage(doc, doc.getPage(i), font, result.wordBoxes(), dpi);
            }

            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                doc.save(bos);
                return bos.toByteArray();
            }
        } catch (IOException e) {
            LOGGER.error("[SearchablePdfWriter#build] write failed", e);
            throw new IllegalStateException("可搜索 PDF 生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 在单页上叠加透明文字层
     */
    private void overlayPage(PDDocument doc, PDPage page, PDFont font,
                             List<OcrResult.WordBox> boxes, int dpi) throws IOException {
        PDRectangle mediaBox = page.getMediaBox();
        float pageHeightPt = mediaBox.getHeight();

        try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                PDPageContentStream.AppendMode.APPEND, true, true)) {
            for (OcrResult.WordBox box : boxes) {
                // 像素坐标 → PDF 坐标（单位 pt），y 轴翻转
                float scale = 72.0f / dpi;
                float x = box.x() * scale;
                float y = pageHeightPt - (box.y() + box.height()) * scale;
                float w = box.width() * scale;
                float h = box.height() * scale;

                if (w <= 0 || h <= 0 || x < 0 || y < 0) {
                    continue;
                }

                cs.beginText();
                // 不可见文字层（RenderingMode.NEITHER = Tr 3）：
                // 文字完全不渲染，但保留在内容流中用于搜索/复制，底图保持清晰
                cs.setRenderingMode(RenderingMode.NEITHER);
                cs.setFont(font, Math.max(1f, h * 0.8f));
                cs.newLineAtOffset(x, y);
                cs.showText(escapeText(box.text()));
                cs.endText();
            }
        }
    }

    /**
     * 加载支持中文的字体：配置 > 系统探测
     */
    private PDFont loadFont(PDDocument doc) throws IOException {
        if (fontPath != null && !fontPath.isBlank()) {
            Path custom = Path.of(fontPath);
            if (Files.exists(custom)) {
                PDFont font = tryLoad(doc, custom);
                if (font != null) {
                    return font;
                }
            }
        }
        for (String p : CANDIDATE_FONT_PATHS) {
            Path path = Path.of(p);
            if (Files.exists(path)) {
                PDFont font = tryLoad(doc, path);
                if (font != null) {
                    LOGGER.info("[SearchablePdfWriter#loadFont] using font: {}", p);
                    return font;
                }
            }
        }
        throw new IOException("未找到支持中文的字体，无法生成可搜索 PDF");
    }

    /**
     * 尝试加载单个字体文件（.ttf/.otf 直接加载，.ttc 取第一个子字体）
     */
    private PDFont tryLoad(PDDocument doc, Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            if (path.toString().toLowerCase().endsWith(".ttc")) {
                TrueTypeCollection ttc = new TrueTypeCollection(in);
                final PDFont[] holder = new PDFont[1];
                ttc.processAllFonts(ttf -> {
                    if (holder[0] == null) {
                        holder[0] = PDType0Font.load(doc, ttf, true);
                    }
                });
                return holder[0];
            }
            return PDType0Font.load(doc, in, true);
        } catch (Exception e) {
            LOGGER.warn("[SearchablePdfWriter#tryLoad] font load failed for {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 转义 PDF 文本中的特殊字符
     */
    private static String escapeText(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }
}
