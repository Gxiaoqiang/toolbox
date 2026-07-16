package com.toolbox.service.pdf.impl;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.pdf.PdfCompressConstant;
import com.toolbox.service.pdf.PdfCompressConstant.CompressLevel;
import com.toolbox.service.pdf.PdfCompressResult;
import com.toolbox.service.pdf.PdfCompressService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * PDF 压缩服务实现 — 基于 PDFBox 图片降采样 + JPEG 重编码
 *
 * @author toolbox
 * @since 2026-07-14
 */
@Service
public class PdfCompressServiceImpl implements PdfCompressService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfCompressServiceImpl.class);

    @Override
    public PdfCompressResult compress(byte[] pdfBytes, String originalFilename, int level) {
        // 1. 参数校验
        CompressLevel compressLevel;
        try {
            compressLevel = CompressLevel.fromLevel(level);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCodeEnum.PDF_COMPRESS_LEVEL_INVALID);
        }

        long originalSize = pdfBytes.length;
        LOGGER.info("[PdfCompressServiceImpl#compress] file={}, originalSize={}, level={}({})",
                originalFilename, originalSize, compressLevel.getLevel(), compressLevel.getLabel());

        // 2. 加载 PDF 并压缩
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            // 检查是否加密
            if (doc.isEncrypted()) {
                throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPTED);
            }

            int totalPages = doc.getNumberOfPages();
            int totalImages = 0;
            int compressedImages = 0;

            // 3. 逐页压缩图片
            for (PDPage page : doc.getPages()) {
                PDResources resources = page.getResources();
                if (resources == null) continue;

                Iterable<COSName> xobjNames = resources.getXObjectNames();
                PDRectangle mediaBox = page.getMediaBox();
                // 计算页面尺寸（英寸）
                float pageWInches = mediaBox.getWidth() / 72.0f;
                float pageHInches = mediaBox.getHeight() / 72.0f;
                // 目标 DPI 下全页图片的最大像素尺寸
                int maxTargetW = Math.max(1, (int) (pageWInches * compressLevel.getTargetDpi()));
                int maxTargetH = Math.max(1, (int) (pageHInches * compressLevel.getTargetDpi()));

                for (COSName name : xobjNames) {
                    PDXObject xobj = resources.getXObject(name);
                    if (!(xobj instanceof PDImageXObject img)) continue;

                    totalImages++;

                    try {
                        // 获取原始图片
                        BufferedImage original = img.getImage();
                        if (original == null) continue;

                        int imgW = original.getWidth();
                        int imgH = original.getHeight();

                        // 计算降采样比例：超过目标 DPI 对应像素才缩放
                        float scaleW = (imgW > maxTargetW) ? (float) maxTargetW / imgW : 1.0f;
                        float scaleH = (imgH > maxTargetH) ? (float) maxTargetH / imgH : 1.0f;
                        float scale = Math.min(scaleW, scaleH);

                        BufferedImage processed;
                        if (scale < 0.95f) {
                            int newW = Math.max(1, Math.round(imgW * scale));
                            int newH = Math.max(1, Math.round(imgH * scale));
                            processed = scaleImage(original, newW, newH);
                            compressedImages++;
                        } else {
                            processed = original;
                        }

                        // 转换为 RGB 并创建 JPEG PDImageXObject
                        BufferedImage rgbImage = toRgb(processed);
                        PDImageXObject newImg = JPEGFactory.createFromImage(doc, rgbImage,
                                compressLevel.getJpegQuality());
                        resources.put(name, newImg);

                    } catch (Exception e) {
                        // 单张图片处理失败不中断整体流程，保留原图
                        LOGGER.warn("[PdfCompressServiceImpl#compress] skip image on page {}, name={}: {}",
                                doc.getPages().indexOf(page) + 1, name.getName(), e.getMessage());
                    }
                }
            }

            LOGGER.info("[PdfCompressServiceImpl#compress] {} pages, {} images, {} downsampled",
                    totalPages, totalImages, compressedImages);

            // 4. 元数据处理
            if (compressLevel.isRemoveMeta()) {
                removeMetadata(doc);
            }

            // 5. 保存为压缩后的 PDF
            byte[] compressedData;
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                doc.save(bos);
                compressedData = bos.toByteArray();
            }

            long compressedSize = compressedData.length;
            double ratio = (1.0 - (double) compressedSize / originalSize) * 100;
            LOGGER.info("[PdfCompressServiceImpl#compress] done: {} -> {} bytes, ratio={}%",
                    originalSize, compressedSize, String.format("%.1f", ratio));

            return new PdfCompressResult(compressedData, originalSize, compressedSize);

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            LOGGER.error("[PdfCompressServiceImpl#compress] error: file={}", originalFilename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_COMPRESS_PROCESS_ERROR);
        }
    }

    // ======================== 图片处理 ========================

    /**
     * 高质量缩放图片
     */
    private static BufferedImage scaleImage(BufferedImage source, int targetW, int targetH) {
        BufferedImage scaled = new BufferedImage(targetW, targetH,
                source.getTransparency() == BufferedImage.OPAQUE
                        ? BufferedImage.TYPE_INT_RGB
                        : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, AffineTransform.getScaleInstance(
                    (double) targetW / source.getWidth(),
                    (double) targetH / source.getHeight()), null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    /**
     * 转为 RGB 图像（JPEG 不支持透明度）
     */
    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    // ======================== 元数据处理 ========================

    /**
     * 移除文档元数据（作者、创建者、生产者等）
     */
    private static void removeMetadata(PDDocument doc) {
        // 清空文档信息
        if (doc.getDocumentInformation() != null) {
            var info = doc.getDocumentInformation();
            info.setTitle(null);
            info.setAuthor(null);
            info.setSubject(null);
            info.setKeywords(null);
            info.setCreator(null);
            info.setProducer(null);
        }
        // 移除 XMP 元数据流
        PDMetadata meta = doc.getDocumentCatalog().getMetadata();
        if (meta != null) {
            try {
                doc.getDocumentCatalog().setMetadata(null);
            } catch (Exception ignored) {
                // 某些 PDF 的元数据流不可变，忽略即可
            }
        }
    }
}
