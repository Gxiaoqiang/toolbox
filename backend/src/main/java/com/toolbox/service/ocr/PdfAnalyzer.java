package com.toolbox.service.ocr;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 页面类型检测器
 * <p>
 * 逐页判断是扫描件（图片型）还是原生文字，避免对原生文字页浪费 OCR 资源。
 * 判断逻辑：
 * <ol>
 *     <li>提取文本为空或仅空白 → 扫描页</li>
 *     <li>文本长度小于阈值且页面含图片资源 → 扫描页</li>
 *     <li>否则 → 原生文字页</li>
 * </ol>
 *
 * @author toolbox
 * @since 2026-08-04
 */
@Component
public class PdfAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfAnalyzer.class);

    /** 文本长度阈值：小于该值且含图片的页面视为扫描页 */
    private static final int MIN_TEXT_LENGTH = 10;

    /**
     * 页面类型枚举
     */
    public enum PageType {
        /** 扫描件，需 OCR */
        SCANNED,
        /** 原生文字，直接提取 */
        NATIVE
    }

    /**
     * 逐页分类
     *
     * @param doc PDFBox 文档
     * @return 每页的类型标记，下标与页码一致
     */
    public List<PageType> classify(PDDocument doc) {
        int total = doc.getNumberOfPages();
        List<PageType> types = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            types.add(classifyPage(doc, i));
        }

        int scanned = (int) types.stream().filter(t -> t == PageType.SCANNED).count();
        LOGGER.info("[PdfAnalyzer#classify] {} pages, {} scanned, {} native", total, scanned, total - scanned);
        return types;
    }

    /**
     * 分类单页
     */
    private PageType classifyPage(PDDocument doc, int pageIndex) {
        String text = extractText(doc, pageIndex);
        if (text == null || text.trim().isEmpty()) {
            return PageType.SCANNED;
        }
        if (text.trim().length() < MIN_TEXT_LENGTH && hasImage(doc.getPage(pageIndex))) {
            return PageType.SCANNED;
        }
        return PageType.NATIVE;
    }

    /**
     * 提取单页文本
     */
    private String extractText(PDDocument doc, int pageIndex) {
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            return stripper.getText(doc);
        } catch (IOException e) {
            LOGGER.warn("[PdfAnalyzer#extractText] page {} extraction failed: {}", pageIndex + 1, e.getMessage());
            return null;
        }
    }

    /**
     * 判断页面是否包含图片资源
     */
    private boolean hasImage(PDPage page) {
        PDResources resources = page.getResources();
        if (resources == null) {
            return false;
        }
        for (COSName name : resources.getXObjectNames()) {
            try {
                PDXObject xobj = resources.getXObject(name);
                if (xobj instanceof PDImageXObject) {
                    return true;
                }
            } catch (IOException e) {
                // 忽略单个资源读取失败
            }
        }
        return false;
    }
}
