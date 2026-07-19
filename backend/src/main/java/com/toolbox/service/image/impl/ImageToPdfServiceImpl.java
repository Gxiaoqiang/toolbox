package com.toolbox.service.image.impl;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.image.ImageToPdfService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 图片转 PDF 服务实现——基于 Apache PDFBox
 *
 * @author toolbox
 * @since 2026-07-19
 */
@Service
public class ImageToPdfServiceImpl implements ImageToPdfService {

    private static final Logger log = LoggerFactory.getLogger(ImageToPdfServiceImpl.class);

    /** A4 纵向尺寸 (pt) */
    private static final float A4_WIDTH = PDRectangle.A4.getWidth();   // 595
    private static final float A4_HEIGHT = PDRectangle.A4.getHeight(); // 842

    @Override
    public byte[] convertToPdf(List<byte[]> imageBytesList, List<String> extensions,
                               String orientation, String margin, String fitMode) {

        log.info("[ImageToPdfServiceImpl#convertToPdf] {} images, orientation={}, margin={}, fitMode={}",
                imageBytesList.size(), orientation, margin, fitMode);

        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < imageBytesList.size(); i++) {
                byte[] imgBytes = imageBytesList.get(i);
                String ext = extensions.get(i);

                // 1. 加载图片（GIF 取第一帧）
                BufferedImage bufferedImage = loadFirstFrame(imgBytes, ext);
                if (bufferedImage == null) {
                    throw new BusinessException(ErrorCodeEnum.IMAGE_FORMAT_UNSUPPORTED);
                }

                // 2. 创建页面（根据方向确定尺寸）
                float pageWidth = "landscape".equals(orientation) ? A4_HEIGHT : A4_WIDTH;
                float pageHeight = "landscape".equals(orientation) ? A4_WIDTH : A4_HEIGHT;
                PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
                doc.addPage(page);

                // 3. 计算可绘制区域（减去边距）
                float marginPt = resolveMargin(margin);
                float drawX = marginPt;
                float drawY = marginPt;
                float drawWidth = pageWidth - 2 * marginPt;
                float drawHeight = pageHeight - 2 * marginPt;

                // 4. 根据 fitMode 计算图片绘制尺寸和偏移
                float imgWidth = bufferedImage.getWidth();
                float imgHeight = bufferedImage.getHeight();

                float[] drawParams = calculateDrawParams(
                        imgWidth, imgHeight, drawWidth, drawHeight,
                        drawX, drawY, fitMode);

                // drawParams: [destX, destY, destWidth, destHeight]
                float destX = drawParams[0];
                float destY = drawParams[1];
                float destWidth = drawParams[2];
                float destHeight = drawParams[3];

                // 5. 将 BufferedImage 转为 PNG 字节数组（PDFBox 需要 PDImageXObject）
                byte[] pngBytes = toPngBytes(bufferedImage);
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, pngBytes, ext);

                // 6. 绘制到页面
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(pdImage, destX, destY, destWidth, destHeight);
                }
            }

            // 7. 导出
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            log.info("[ImageToPdfServiceImpl#convertToPdf] output {} pages", doc.getNumberOfPages());
            return out.toByteArray();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ImageToPdfServiceImpl#convertToPdf] process failed", e);
            throw new BusinessException(ErrorCodeEnum.IMAGE_TO_PDF_PROCESS_ERROR);
        }
    }

    // ===== 辅助方法 =====

    /**
     * 加载图片第一帧。
     * GIF 格式 ImageIO.read() 天然返回第一帧。
     */
    private BufferedImage loadFirstFrame(byte[] imgBytes, String ext) {
        try {
            // 对 GIF，ImageIO.read() 返回第一帧
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imgBytes));
            return image;
        } catch (Exception e) {
            log.warn("[ImageToPdfServiceImpl#loadFirstFrame] failed to load image, ext={}", ext, e);
            return null;
        }
    }

    /**
     * 将 BufferedImage 转为 PNG 字节数组。
     * PDFBox 的 PDImageXObject.createFromByteArray 支持 PNG/JPEG，统一转 PNG 最安全。
     */
    private byte[] toPngBytes(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * 解析边距值（pt）
     */
    private float resolveMargin(String margin) {
        if (margin == null) return 36f; // 默认 small
        return switch (margin) {
            case "none" -> 0f;
            case "large" -> 72f;
            default -> 36f; // small
        };
    }

    /**
     * 根据 fitMode 计算图片绘制参数。
     *
     * @return [destX, destY, destWidth, destHeight]
     */
    private float[] calculateDrawParams(float imgWidth, float imgHeight,
                                         float drawWidth, float drawHeight,
                                         float drawX, float drawY, String fitMode) {
        if (fitMode == null) fitMode = "contain";

        return switch (fitMode) {
            case "cover" -> calculateCover(imgWidth, imgHeight, drawWidth, drawHeight, drawX, drawY);
            case "stretch" -> calculateStretch(drawWidth, drawHeight, drawX, drawY);
            default -> calculateContain(imgWidth, imgHeight, drawWidth, drawHeight, drawX, drawY);
        };
    }

    /**
     * contain: 等比缩放，完整显示，居中。取 min(scaleX, scaleY)。
     */
    private float[] calculateContain(float imgW, float imgH,
                                      float drawW, float drawH,
                                      float drawX, float drawY) {
        float scale = Math.min(drawW / imgW, drawH / imgH);
        float destW = imgW * scale;
        float destH = imgH * scale;
        float destX = drawX + (drawW - destW) / 2;
        float destY = drawY + (drawH - destH) / 2;
        return new float[]{destX, destY, destW, destH};
    }

    /**
     * cover: 等比缩放，填满页面，居中裁剪。取 max(scaleX, scaleY)。
     * 超出部分由 PDF 渲染器自然裁剪。
     */
    private float[] calculateCover(float imgW, float imgH,
                                    float drawW, float drawH,
                                    float drawX, float drawY) {
        float scale = Math.max(drawW / imgW, drawH / imgH);
        float destW = imgW * scale;
        float destH = imgH * scale;
        float destX = drawX + (drawW - destW) / 2;
        float destY = drawY + (drawH - destH) / 2;
        return new float[]{destX, destY, destW, destH};
    }

    /**
     * stretch: 拉伸填满，不保持比例。
     */
    private float[] calculateStretch(float drawW, float drawH,
                                      float drawX, float drawY) {
        return new float[]{drawX, drawY, drawW, drawH};
    }
}
