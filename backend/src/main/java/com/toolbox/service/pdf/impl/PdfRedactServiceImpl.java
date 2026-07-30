package com.toolbox.service.pdf.impl;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.pdf.RedactRequest;
import com.toolbox.service.pdf.PdfRedactService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PDF 涂黑遮盖服务实现 — 基于 PDFBox
 *
 * @author toolbox
 * @since 2026-07-30
 */
@Service
public class PdfRedactServiceImpl implements PdfRedactService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfRedactServiceImpl.class);

    private static final String MODE_STANDARD = "standard";
    private static final String MODE_DEEP = "deep";

    /** 深度遮盖模式下的渲染 DPI */
    private static final int DEEP_RENDER_DPI = 200;

    @Override
    public byte[] redact(byte[] pdfBytes, String originalFilename, RedactRequest request) {
        // 1. 参数校验
        validateRequest(request);

        String mode = request.getMode();
        List<RedactRequest.RectItem> rects = request.getRects();

        LOGGER.info("[PdfRedactServiceImpl#redact] file={}, mode={}, rects={}",
                originalFilename, mode, rects.size());

        // 2. 按页码分组方块
        Map<Integer, List<RedactRequest.RectItem>> rectsByPage = rects.stream()
                .collect(Collectors.groupingBy(RedactRequest.RectItem::getPage));

        // 3. 加载 PDF 并执行遮盖
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            // 3.1 检查加密
            if (doc.isEncrypted()) {
                throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPTED);
            }

            int totalPages = doc.getNumberOfPages();

            // 3.2 逐页执行遮盖
            for (Map.Entry<Integer, List<RedactRequest.RectItem>> entry : rectsByPage.entrySet()) {
                int pageIndex = entry.getKey();
                // 忽略越界页码
                if (pageIndex < 0 || pageIndex >= totalPages) {
                    LOGGER.warn("[PdfRedactServiceImpl#redact] skip out-of-range page={}, totalPages={}",
                            pageIndex, totalPages);
                    continue;
                }

                List<RedactRequest.RectItem> pageRects = entry.getValue();
                if (MODE_DEEP.equals(mode)) {
                    redactDeep(doc, pageIndex, pageRects);
                } else {
                    redactStandard(doc, pageIndex, pageRects);
                }
            }

            // 3.3 保存输出
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                doc.save(bos);
                byte[] result = bos.toByteArray();
                LOGGER.info("[PdfRedactServiceImpl#redact] done: file={}, pages={}, totalRects={}, resultSize={}",
                        originalFilename, totalPages, rects.size(), result.length);
                return result;
            }

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            LOGGER.error("[PdfRedactServiceImpl#redact] error: file={}", originalFilename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_REDACT_PROCESS_ERROR);
        }
    }

    // ======================== 标准遮盖 ========================

    /**
     * 标准遮盖：在现有 PDF 页面内容流上追加不透明矩形
     * 新绘制的内容位于已有内容之上，实现遮盖效果
     *
     * @param doc       PDF 文档
     * @param pageIndex 页码（0-based）
     * @param rects     该页的方块列表
     */
    private void redactStandard(PDDocument doc, int pageIndex, List<RedactRequest.RectItem> rects) throws IOException {
        PDPage page = doc.getPage(pageIndex);
        PDRectangle mediaBox = page.getMediaBox();
        float pageHeight = mediaBox.getHeight();

        try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

            for (RedactRequest.RectItem rect : rects) {
                // PDF 坐标系原点在左下角，前端传来的 Y 左上角坐标需翻转
                float pdfX = (float) rect.getX();
                float pdfY = pageHeight - (float) rect.getY() - (float) rect.getH();
                float pdfW = (float) rect.getW();
                float pdfH = (float) rect.getH();

                // 确保坐标不超出页面边界
                pdfX = Math.max(0, pdfX);
                pdfY = Math.max(0, pdfY);
                pdfW = Math.min(pdfW, mediaBox.getWidth() - pdfX);
                pdfH = Math.min(pdfH, pageHeight - pdfY);

                Color awtColor = parseColor(rect.getColor());
                cs.setNonStrokingColor(awtColor);
                cs.addRect(pdfX, pdfY, pdfW, pdfH);
                cs.fill();
            }
        }

        LOGGER.info("[PdfRedactServiceImpl#redactStandard] page={}, rects={}", pageIndex, rects.size());
    }

    // ======================== 深度遮盖 ========================

    /**
     * 深度遮盖：将页面渲染为图片，在图片上绘制方块，再替换回 PDF 页面
     * 彻底消除底层所有内容（文字、图片、路径），安全性最高
     *
     * @param doc       PDF 文档
     * @param pageIndex 页码（0-based）
     * @param rects     该页的方块列表
     */
    private void redactDeep(PDDocument doc, int pageIndex, List<RedactRequest.RectItem> rects) throws IOException {
        PDPage page = doc.getPage(pageIndex);
        PDRectangle mediaBox = page.getMediaBox();
        float pageWidth = mediaBox.getWidth();
        float pageHeight = mediaBox.getHeight();

        // 1. 页面渲染为图片
        PDFRenderer renderer = new PDFRenderer(doc);
        BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, DEEP_RENDER_DPI);

        // 2. 计算缩放比：图片像素 / PDF point
        float scaleX = pageImage.getWidth() / pageWidth;
        float scaleY = pageImage.getHeight() / pageHeight;

        // 3. 在图片上绘制遮盖方块
        Graphics2D g = pageImage.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            for (RedactRequest.RectItem rect : rects) {
                int imgX = Math.round((float) rect.getX() * scaleX);
                int imgY = Math.round((float) rect.getY() * scaleY);
                int imgW = Math.round((float) rect.getW() * scaleX);
                int imgH = Math.round((float) rect.getH() * scaleY);

                // 边界裁剪
                imgX = Math.max(0, imgX);
                imgY = Math.max(0, imgY);
                imgW = Math.min(imgW, pageImage.getWidth() - imgX);
                imgH = Math.min(imgH, pageImage.getHeight() - imgY);

                g.setColor(parseColor(rect.getColor()));
                g.fillRect(imgX, imgY, imgW, imgH);
            }
        } finally {
            g.dispose();
        }

        // 4. 用遮盖后的图片替换页面内容
        // OVERWRITE 模式会替换页面的内容流

        try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.OVERWRITE, true)) {
            PDImageXObject imgObj = PDImageXObject.createFromByteArray(
                    doc, bufferedImageToByteArray(pageImage), "redacted-page-" + pageIndex);
            cs.drawImage(imgObj, 0, 0, pageWidth, pageHeight);
        }

        LOGGER.info("[PdfRedactServiceImpl#redactDeep] page={}, rects={}", pageIndex, rects.size());
    }

    // ======================== 辅助方法 ========================

    /**
     * 校验遮盖请求参数
     */
    private static void validateRequest(RedactRequest request) {
        if (request == null || request.getRects() == null || request.getRects().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_REDACT_RECTS_EMPTY);
        }
        String mode = request.getMode();
        if (mode == null || (!MODE_STANDARD.equals(mode) && !MODE_DEEP.equals(mode))) {
            throw new BusinessException(ErrorCodeEnum.PDF_REDACT_MODE_INVALID);
        }
        for (RedactRequest.RectItem rect : request.getRects()) {
            if (!rect.isValid()) {
                throw new BusinessException(ErrorCodeEnum.PDF_REDACT_RECTS_FORMAT_ERROR);
            }
        }
    }

    /**
     * 解析 hex 颜色字符串为 AWT Color
     *
     * @param hex 颜色字符串（如 "#000000" 或 "000000"）
     * @return AWT Color 对象，解析失败返回黑色
     */
    private static Color parseColor(String hex) {
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            int rgb = Integer.parseInt(clean, 16);
            return new Color(rgb);
        } catch (Exception e) {
            LOGGER.warn("[PdfRedactServiceImpl#parseColor] invalid color '{}', fallback to black", hex);
            return Color.BLACK;
        }
    }

    /**
     * BufferedImage 转字节数组（PNG 格式）
     */
    private static byte[] bufferedImageToByteArray(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", bos);
            return bos.toByteArray();
        }
    }
}
