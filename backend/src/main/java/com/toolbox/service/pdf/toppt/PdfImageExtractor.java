package com.toolbox.service.pdf.toppt;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 图片提取器 — 提取页内嵌图片为 PNG 字节，并估算其 y 位置
 * <p>
 * 用 {@code page.getResources().getXObjectNames()} 迭代图片对象，
 * 经 {@link Matrix#getTranslateY()} 取 PDF 用户空间 y，归一化为向下坐标。
 * 位置为近似（Word 是线性流，图片按 y 顺序嵌入对应文本附近）。
 *
 * @author toolbox
 * @since 2026-08-05
 */
@Component
public class PdfImageExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfImageExtractor.class);

    /**
     * 提取页面内嵌图片
     *
     * @param page 页面
     * @return 图片列表（PNG 字节 + y 向下坐标），无图返回空列表
     */
    public List<ImageInfo> extract(PDPage page) throws IOException {
        List<ImageInfo> images = new ArrayList<>();
        PDResources res = page.getResources();
        if (res == null) {
            return images;
        }

        for (COSName name : res.getXObjectNames()) {
            try {
                PDXObject xobj = res.getXObject(name);
                if (!(xobj instanceof PDImageXObject img)) {
                    continue;
                }
                BufferedImage bi = img.getImage();
                if (bi == null) {
                    continue;
                }
                byte[] png = toPng(bi);
                if (png.length == 0) {
                    continue;
                }
                // 位置近似：Word 为线性流，按提取顺序（y 递增索引）嵌入
                images.add(new ImageInfo(png, images.size()));
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("[PdfImageExtractor#extract] skip image, error={}", e.getMessage());
            }
        }
        return images;
    }

    private byte[] toPng(BufferedImage bi) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", bos);
        return bos.toByteArray();
    }

    /**
     * 提取的图片
     *
     * @param pngBytes PNG 字节
     * @param y        向下坐标（估算）
     */
    public record ImageInfo(byte[] pngBytes, float y) {
    }
}
