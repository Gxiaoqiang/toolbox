package com.toolbox.service.ocr.impl;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.ocr.MarkdownBuilder;
import com.toolbox.service.ocr.OcrEngine;
import com.toolbox.service.ocr.OcrProcessResult;
import com.toolbox.service.ocr.OcrResult;
import com.toolbox.service.ocr.OcrService;
import com.toolbox.service.ocr.PdfAnalyzer;
import com.toolbox.service.ocr.PdfAnalyzer.PageType;
import com.toolbox.service.ocr.SearchablePdfWriter;
import com.toolbox.service.ocr.TableExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PDF OCR 识别服务实现
 * <p>
 * 编排完整流程：
 * <ol>
 *     <li>PdfAnalyzer 逐页检测扫描件 / 原生文字</li>
 *     <li>原生页直接提取文本，扫描页渲染为图片交给 OcrEngine 识别</li>
 *     <li>按输出格式组装：可搜索 PDF / 纯文本 / Markdown / Excel</li>
 * </ol>
 *
 * @author toolbox
 * @since 2026-08-04
 */
@Service
public class OcrServiceImpl implements OcrService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OcrServiceImpl.class);

    /** 页面渲染 DPI — OCR 精度与性能的平衡点 */
    private static final int RENDER_DPI = 300;

    private static final String FORMAT_SEARCHABLE_PDF = "searchable_pdf";
    private static final String FORMAT_TEXT = "text";
    private static final String FORMAT_MD = "md";
    private static final String FORMAT_XLSX = "xlsx";

    private static final Set<String> VALID_FORMATS = Set.of(
            FORMAT_SEARCHABLE_PDF, FORMAT_TEXT, FORMAT_MD, FORMAT_XLSX);

    private static final Set<String> VALID_LANGUAGES = Set.of("chi_sim", "eng", "chi_sim+eng");

    private final OcrEngine ocrEngine;
    private final PdfAnalyzer pdfAnalyzer;
    private final SearchablePdfWriter searchablePdfWriter;
    private final MarkdownBuilder markdownBuilder;
    private final TableExtractor tableExtractor;

    public OcrServiceImpl(OcrEngine ocrEngine, PdfAnalyzer pdfAnalyzer,
                          SearchablePdfWriter searchablePdfWriter,
                          MarkdownBuilder markdownBuilder, TableExtractor tableExtractor) {
        this.ocrEngine = ocrEngine;
        this.pdfAnalyzer = pdfAnalyzer;
        this.searchablePdfWriter = searchablePdfWriter;
        this.markdownBuilder = markdownBuilder;
        this.tableExtractor = tableExtractor;
    }

    @Override
    public OcrProcessResult process(byte[] pdfBytes, String originalFilename, String format, String language) {
        // 1. 参数校验
        validateFormat(format);
        validateLanguage(language);
        if (!ocrEngine.isAvailable()) {
            throw new BusinessException(ErrorCodeEnum.PDF_OCR_ENGINE_NOT_AVAILABLE);
        }

        long start = System.currentTimeMillis();
        String baseName = stripExtension(originalFilename);

        PDDocument doc;
        try {
            // 先用空密码加载：真正需要密码的 PDF 会抛 InvalidPasswordException，
            // 而"空密码可打开"的假加密文档（带加密字典但无密码限制）能正常加载
            doc = Loader.loadPDF(pdfBytes);
        } catch (InvalidPasswordException e) {
            throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPTED);
        } catch (IOException e) {
            LOGGER.error("[OcrServiceImpl#process] load pdf failed: {}", originalFilename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_OCR_PROCESS_ERROR);
        }
        try (doc) {
            if (doc.isEncrypted()) {
                // 假加密（空密码可打开）：移除加密字典，避免后续渲染/保存受限
                doc.setAllSecurityToBeRemoved(true);
            }

            // 2. 逐页检测
            List<PageType> types = pdfAnalyzer.classify(doc);
            int totalPages = types.size();
            int scannedPages = (int) types.stream().filter(t -> t == PageType.SCANNED).count();
            int nativePages = totalPages - scannedPages;

            // 3. 提取每页文本（原生页直接提，扫描页走 OCR）
            List<String> pageTexts = new ArrayList<>(totalPages);
            List<OcrResult> ocrResults = new ArrayList<>(totalPages);
            PDFRenderer renderer = new PDFRenderer(doc);

            for (int i = 0; i < totalPages; i++) {
                if (types.get(i) == PageType.NATIVE) {
                    String nativeText = extractNativeText(doc, i);
                    pageTexts.add(nativeText);
                    ocrResults.add(null);
                } else {
                    OcrResult result = ocrPage(renderer, doc, i, language);
                    pageTexts.add(result.text());
                    ocrResults.add(result);
                }
            }

            // 4. 按输出格式组装
            byte[] data;
            String mimeType;
            switch (format) {
                case FORMAT_SEARCHABLE_PDF -> {
                    data = searchablePdfWriter.build(doc, ocrResults, types, RENDER_DPI);
                    mimeType = "application/pdf";
                }
                case FORMAT_TEXT -> {
                    data = buildPlainText(pageTexts).getBytes(StandardCharsets.UTF_8);
                    mimeType = "text/plain; charset=UTF-8";
                }
                case FORMAT_MD -> {
                    data = markdownBuilder.build(pageTexts, ocrResults, types).getBytes(StandardCharsets.UTF_8);
                    mimeType = "text/markdown; charset=UTF-8";
                }
                default -> {
                    data = tableExtractor.extract(pageTexts);
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            String filename = resultFilename(baseName, format);
            LOGGER.info("[OcrServiceImpl#process] file={}, format={}, lang={}, total={}, scanned={}, native={}, elapsed={}ms",
                    originalFilename, format, language, totalPages, scannedPages, nativePages, elapsed);

            return new OcrProcessResult(data, mimeType, filename, totalPages, scannedPages, nativePages, elapsed);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[OcrServiceImpl#process] error: file={}", originalFilename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_OCR_PROCESS_ERROR);
        }
    }

    // ======================== 参数校验 ========================

    private void validateFormat(String format) {
        if (format == null || !VALID_FORMATS.contains(format)) {
            throw new BusinessException(ErrorCodeEnum.PDF_OCR_OUTPUT_FORMAT_INVALID);
        }
    }

    private void validateLanguage(String language) {
        if (language == null || !VALID_LANGUAGES.contains(language)) {
            throw new BusinessException(ErrorCodeEnum.PDF_OCR_LANGUAGE_INVALID);
        }
    }

    // ======================== 文本处理 ========================

    /**
     * 原生页直接提取文本
     */
    private String extractNativeText(PDDocument doc, int pageIndex) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        return stripper.getText(doc);
    }

    /**
     * 扫描页渲染为图片并执行 OCR
     * <p>
     * 直接传递 BufferedImage，不做图片字节编解码，彻底绕开 javax.imageio.ImageIO
     * （fat jar 打包后 ImageIO 的 SPI 加载会失败）
     */
    private OcrResult ocrPage(PDFRenderer renderer, PDDocument doc, int pageIndex, String language) throws Exception {
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI);
        return ocrEngine.recognize(image, language);
    }

    /**
     * 合并纯文本（按页用分隔线分隔）
     */
    private String buildPlainText(List<String> pageTexts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pageTexts.size(); i++) {
            String text = pageTexts.get(i);
            if (text == null || text.isBlank()) {
                continue;
            }
            sb.append("----- 第 ").append(i + 1).append(" 页 -----\n");
            sb.append(text.trim()).append("\n\n");
        }
        return sb.toString().trim() + "\n";
    }

    // ======================== 工具方法 ========================

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String resultFilename(String baseName, String format) {
        return switch (format) {
            case FORMAT_TEXT -> baseName + "-ocr.txt";
            case FORMAT_MD -> baseName + "-ocr.md";
            case FORMAT_XLSX -> baseName + "-ocr.xlsx";
            default -> baseName + "-ocr.pdf";
        };
    }
}
