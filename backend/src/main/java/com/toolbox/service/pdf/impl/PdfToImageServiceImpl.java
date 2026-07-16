package com.toolbox.service.pdf.impl;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.pdf.ImageConvertConstant;
import com.toolbox.service.pdf.ImageConvertConstant.ImageFormat;
import com.toolbox.service.pdf.PdfToImageResult;
import com.toolbox.service.pdf.PdfToImageService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * PDF 转图片服务实现（基于 PDFBox 3.x PDFRenderer）
 *
 * @author toolbox
 * @since 2026-07-14
 */
@Service
public class PdfToImageServiceImpl implements PdfToImageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfToImageServiceImpl.class);

    @Override
    public PdfToImageResult convertToImages(byte[] pdfBytes, String originalFilename,
                                            int dpi, String format, float quality, String pageRange) {
        // 1. 参数校验
        validateParams(dpi, format, quality);
        ImageFormat imageFormat = ImageFormat.from(format);

        // 2. 加载 PDF 并逐页渲染（串行，PDFBox PDDocument 非线程安全）
        List<byte[]> images;
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            if (doc.isEncrypted()) {
                throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPTED);
            }
            List<Integer> pageIndexes = parsePageRange(pageRange, doc.getNumberOfPages());
            images = renderPages(doc, pageIndexes, dpi, imageFormat, quality);
            LOGGER.info("[PdfToImageServiceImpl#convertToImages] done: {} pages -> {} images, format={}, dpi={}",
                    doc.getNumberOfPages(), images.size(), imageFormat.getExtension(), dpi);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PdfToImageServiceImpl#convertToImages] error", e);
            throw new BusinessException(ErrorCodeEnum.PDF_IMAGE_PROCESS_ERROR);
        }

        // 3. 构建结果
        String baseName = stripExtension(originalFilename);
        if (images.size() == 1) {
            return buildSingleResult(images.get(0), baseName, imageFormat);
        }
        return buildZipResult(images, baseName, imageFormat);
    }

    // ======================== 渲染 ========================

    private List<byte[]> renderPages(PDDocument doc, List<Integer> indexes,
                                     int dpi, ImageFormat format, float quality) {
        PDFRenderer renderer = new PDFRenderer(doc);
        List<byte[]> result = new ArrayList<>(indexes.size());
        for (int idx : indexes) {
            try {
                BufferedImage image = renderer.renderImageWithDPI(idx, dpi);
                result.add(writeImage(image, format, quality));
            } catch (Exception e) {
                LOGGER.error("[PdfToImageServiceImpl#renderPages] page {} failed", idx + 1, e);
                throw new BusinessException(ErrorCodeEnum.PDF_IMAGE_PROCESS_ERROR);
            }
        }
        return result;
    }

    // ======================== 参数校验 ========================

    private void validateParams(int dpi, String format, float quality) {
        if (dpi < ImageConvertConstant.MIN_DPI || dpi > ImageConvertConstant.MAX_DPI) {
            throw new BusinessException(ErrorCodeEnum.PDF_IMAGE_DPI_INVALID);
        }
        ImageFormat imageFormat = ImageFormat.from(format);
        if (imageFormat == null) {
            throw new BusinessException(ErrorCodeEnum.PDF_IMAGE_FORMAT_INVALID);
        }
        if (imageFormat == ImageFormat.JPEG && (quality < 0.0f || quality > 1.0f)) {
            throw new BusinessException(ErrorCodeEnum.PDF_IMAGE_QUALITY_INVALID);
        }
    }

    // ======================== 页码范围解析 ========================

    static List<Integer> parsePageRange(String range, int totalPages) {
        if (range == null || range.isBlank()) {
            List<Integer> all = new ArrayList<>(totalPages);
            for (int i = 0; i < totalPages; i++) {
                all.add(i);
            }
            return all;
        }
        List<Integer> result = new ArrayList<>();
        for (String part : range.split(",")) {
            part = part.trim();
            if (part.contains("-")) {
                String[] pair = part.split("-", 2);
                try {
                    int start = Integer.parseInt(pair[0].trim());
                    int end = Integer.parseInt(pair[1].trim());
                    if (start < 1 || end > totalPages || start > end) {
                        throw new BusinessException(ErrorCodeEnum.PDF_PAGE_OUT_OF_RANGE);
                    }
                    for (int i = start - 1; i < end; i++) {
                        if (!result.contains(i)) result.add(i);
                    }
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCodeEnum.PDF_PAGE_FORMAT_ERROR);
                }
            } else {
                try {
                    int page = Integer.parseInt(part);
                    if (page < 1 || page > totalPages) {
                        throw new BusinessException(ErrorCodeEnum.PDF_PAGE_OUT_OF_RANGE);
                    }
                    if (!result.contains(page - 1)) result.add(page - 1);
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCodeEnum.PDF_PAGE_FORMAT_ERROR);
                }
            }
        }
        if (result.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_PAGE_FORMAT_ERROR);
        }
        return result;
    }

    // ======================== 结果构建 ========================

    private PdfToImageResult buildSingleResult(byte[] imageBytes, String baseName, ImageFormat format) {
        String filename = baseName + ImageConvertConstant.SINGLE_PAGE_SUFFIX + "." + format.getExtension();
        return new PdfToImageResult(imageBytes, format.getMimeType(), filename);
    }

    private PdfToImageResult buildZipResult(List<byte[]> images, String baseName, ImageFormat format) {
        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
            int digits = String.valueOf(images.size()).length();
            for (int i = 0; i < images.size(); i++) {
                String entryName = baseName + "-page-"
                        + String.format("%0" + digits + "d", i + 1) + "." + format.getExtension();
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(images.get(i));
                zos.closeEntry();
            }
        } catch (Exception e) {
            LOGGER.error("[PdfToImageServiceImpl#buildZipResult] ZIP failed", e);
            throw new BusinessException(ErrorCodeEnum.PDF_IMAGE_PROCESS_ERROR);
        }
        return new PdfToImageResult(zipOut.toByteArray(), "application/zip", "pdf-images.zip");
    }

    // ======================== 工具方法 ========================

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static byte[] writeImage(BufferedImage image, ImageFormat format, float quality) throws Exception {
        if (format == ImageFormat.JPEG) {
            return writeJpeg(image, quality);
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(image, format.getExtension(), bos);
            return bos.toByteArray();
        }
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) throws Exception {
        BufferedImage rgbImage = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgbImage.createGraphics().drawImage(image, 0, 0, null);
        ImageWriter writer = ImageIO.getImageWritersByFormatName(ImageFormat.JPEG.getExtension()).next();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(ImageIO.createImageOutputStream(bos));
            writer.write(null, new IIOImage(rgbImage, null, null), param);
            return bos.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
