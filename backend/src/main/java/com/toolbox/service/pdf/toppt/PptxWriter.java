package com.toolbox.service.pdf.toppt;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * PPTX 生成器 — 用 Apache POI (XSLF) 将段落 + 底图写成可编辑 PPTX
 * <p>
 * 每页一个 slide：页面尺寸取 PDF 页尺寸（pt → Emu），
 * 放一张整页底图（B1）+ 段落文本框（A2）。
 * 段落 y 已由 {@link PdfTextExtractor} 归一化为向下，与 PPT 坐标一致，直接使用。
 *
 * @author toolbox
 * @since 2026-08-05
 */
@Component
public class PptxWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PptxWriter.class);

    /** 底图渲染 DPI */
    public static final int BACKGROUND_DPI = 150;

    /** 默认中文字体 */
    private static final String CJK_FONT = "微软雅黑";

    /**
     * 生成 PPTX
     *
     * @param pages      每页数据（页宽/高 pt + 段落 + 渲染的底图）
     * @return PPTX 字节
     * @throws IOException 生成失败
     */
    public byte[] write(List<PageData> pages) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            for (PageData page : pages) {
                XSLFSlide slide = pptx.createSlide();

                // 1. 页面尺寸（setPageSize 参数为 pt，直接使用 PDF 页尺寸 pt）
                pptx.setPageSize(new java.awt.Dimension((int) page.width, (int) page.height));

                // 2. 整页底图（POI 坐标单位为 point，直接使用 PDF 页尺寸 pt）
                if (page.backgroundPng != null) {
                    XSLFPictureData pic = pptx.addPicture(page.backgroundPng, PictureData.PictureType.PNG);
                    XSLFPictureShape shape = slide.createPicture(pic);
                    shape.setAnchor(new Rectangle2D.Double(0, 0, page.width, page.height));
                }

                // 3. 段落文本框（y 已归一化为向下，与 PPT 一致，直接使用 pt）
                for (Paragraph p : page.paragraphs) {
                    XSLFTextShape tb = slide.createTextBox();
                    tb.setAnchor(new Rectangle2D.Double(
                            p.x(), p.y(), p.width() + 2, p.height()));
                    tb.setWordWrap(false);

                    String[] lines = p.text().split("\n");
                    for (int li = 0; li < lines.length; li++) {
                        XSLFTextParagraph tp = li == 0
                                ? tb.getTextParagraphs().get(0)
                                : tb.addNewTextParagraph();
                        XSLFTextRun run = tp.addNewTextRun();
                        run.setText(lines[li]);
                        run.setFontSize(Math.max(6d, p.fontSize()));
                        run.setFontFamily(CJK_FONT);
                        tp.setTextAlign(TextParagraph.TextAlign.LEFT);
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pptx.write(out);
            LOGGER.info("[PptxWriter#write] slides={}", pages.size());
            return out.toByteArray();
        }
    }

    /**
     * 渲染整页底图（PNG）
     *
     * @param image 已渲染的页面图像
     * @return PNG 字节
     * @throws IOException 编码失败
     */
    public byte[] encodeBackground(BufferedImage image) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        return bos.toByteArray();
    }

    /**
     * 单页转换数据
     *
     * @param width        页宽（pt）
     * @param height       页高（pt）
     * @param backgroundPng 底图 PNG（可为 null）
     * @param paragraphs   段落文本框
     */
    public record PageData(float width, float height, byte[] backgroundPng, List<Paragraph> paragraphs) {
    }
}
