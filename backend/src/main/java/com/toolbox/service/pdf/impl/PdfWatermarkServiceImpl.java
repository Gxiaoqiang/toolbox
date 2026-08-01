package com.toolbox.service.pdf.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.pdf.WatermarkRequest;
import com.toolbox.service.pdf.PdfWatermarkService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PDF 添加水印服务实现 — 基于 {@link WatermarkRenderer} 追加绘制
 *
 * @author toolbox
 * @since 2026-08-02
 */
@Service
public class PdfWatermarkServiceImpl implements PdfWatermarkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfWatermarkServiceImpl.class);

    private static final String SOURCE_TEXT = "text";
    private static final String SOURCE_IMAGE = "image";

    /** 捆绑的中文字体资源路径（Noto Sans SC Regular，TrueType，OFL 许可） */
    private static final String CJK_FONT_RESOURCE = "/fonts/NotoSansSC-Regular.ttf";

    @Override
    public byte[] addWatermark(byte[] pdfBytes, String originalFilename, WatermarkRequest request, byte[] imageBytes) {
        // 1. 参数校验
        validateRequest(request, imageBytes);

        LOGGER.info("[PdfWatermarkServiceImpl#addWatermark] file={}, source={}, text={}",
                originalFilename, request.getSource(), request.getText());

        // 2. 加载 PDF 并逐页绘制水印
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            // 2.1 检查加密
            if (doc.isEncrypted()) {
                throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPTED);
            }

            // 2.2 计算目标页
            List<Integer> targetPages = computeTargetPages(doc.getNumberOfPages(), request);

            if (SOURCE_TEXT.equals(request.getSource())) {
                // 2.3 文本水印：整文档嵌入一次中文字体，再逐页绘制
                PDFont font = loadCjkFont(doc);
                for (int pageIndex : targetPages) {
                    PDPage page = doc.getPage(pageIndex - 1);
                    WatermarkRenderer.renderText(doc, page, font, request);
                }
            } else if (SOURCE_IMAGE.equals(request.getSource())) {
                // 图片水印在后续工单实现
                throw new BusinessException(ErrorCodeEnum.PDF_WATERMARK_IMAGE_INVALID);
            }

            // 2.4 保存输出
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                doc.save(bos);
                LOGGER.info("[PdfWatermarkServiceImpl#addWatermark] done: file={}, totalPages={}, targetPages={}, resultSize={}",
                        originalFilename, doc.getNumberOfPages(), targetPages.size(), bos.size());
                return bos.toByteArray();
            }

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            LOGGER.error("[PdfWatermarkServiceImpl#addWatermark] error: file={}", originalFilename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_WATERMARK_PROCESS_ERROR);
        }
    }

    /**
     * 校验水印请求参数
     */
    private static void validateRequest(WatermarkRequest request, byte[] imageBytes) {
        if (request == null) {
            throw new BusinessException(ErrorCodeEnum.PDF_WATERMARK_CONFIG_INVALID);
        }
        String source = request.getSource();
        if (SOURCE_TEXT.equals(source)) {
            if (request.getText() == null || request.getText().isBlank()) {
                throw new BusinessException(ErrorCodeEnum.PDF_WATERMARK_TEXT_EMPTY);
            }
        } else if (SOURCE_IMAGE.equals(source)) {
            if (imageBytes == null || imageBytes.length == 0) {
                throw new BusinessException(ErrorCodeEnum.PDF_WATERMARK_IMAGE_INVALID);
            }
        } else {
            throw new BusinessException(ErrorCodeEnum.PDF_WATERMARK_SOURCE_INVALID);
        }
    }

    /**
     * 计算目标页（1-based），按「范围 → 子集」两层递进筛选
     */
    private static List<Integer> computeTargetPages(int totalPages, WatermarkRequest request) {
        int start;
        int end;
        if ("pageRange".equals(request.getRange())) {
            int from = request.getFromPage() != null ? request.getFromPage() : 1;
            int to = request.getToPage() != null ? request.getToPage() : totalPages;
            start = Math.max(1, Math.min(from, to));
            end = Math.min(totalPages, Math.max(from, to));
        } else {
            start = 1;
            end = totalPages;
        }

        String subset = request.getSubset() != null ? request.getSubset() : "all";
        List<Integer> pages = new ArrayList<>();
        for (int p = start; p <= end; p++) {
            switch (subset) {
                case "odd" -> { if (p % 2 == 1) pages.add(p); }
                case "even" -> { if (p % 2 == 0) pages.add(p); }
                default -> pages.add(p);
            }
        }
        return pages;
    }

    /**
     * 从 classpath 加载捆绑的中文字体（CFF OTF）
     */
    private static PDFont loadCjkFont(PDDocument doc) throws IOException {
        try (InputStream in = PdfWatermarkServiceImpl.class.getResourceAsStream(CJK_FONT_RESOURCE)) {
            if (in == null) {
                throw new IOException("CJK font resource not found: " + CJK_FONT_RESOURCE);
            }
            return PDType0Font.load(doc, in);
        }
    }
}
