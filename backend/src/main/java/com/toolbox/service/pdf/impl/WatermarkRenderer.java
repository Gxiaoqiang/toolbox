package com.toolbox.service.pdf.impl;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.toolbox.model.pdf.WatermarkRequest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PDF 添加水印 — 绘制引擎
 * <p>
 * 在目标页内容流【追加】绘制水印（不动原内容），支持文字水印。
 * 位置采用「双轴对齐 + 偏移」模型，绕水印自身中心旋转，支持透明度。
 * 坐标与前端预览共用同一套数学，避免视觉偏差。
 *
 * @author toolbox
 * @since 2026-08-02
 */
public class WatermarkRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatermarkRenderer.class);

    /** 1 厘米对应的 PDF 点（pt） */
    private static final float CM_TO_PT = 28.3465f;

    /** 文字行高系数（用于估算文字块高度） */
    private static final float LINE_HEIGHT_FACTOR = 1.2f;

    private WatermarkRenderer() {
    }

    /**
     * 在页面上绘制文字水印
     *
     * @param doc     当前打开的 PDF 文档
     * @param page    目标页面
     * @param font    已嵌入的中文字体（可为空，为空用 Helvetica）
     * @param request 水印配置
     * @throws IOException 绘制失败
     */
    public static void renderText(PDDocument doc, PDPage page, PDFont font, WatermarkRequest request) throws IOException {
        PDRectangle media = page.getMediaBox();
        float pageWidth = media.getWidth();
        float pageHeight = media.getHeight();

        String text = request.getText();
        float fontSize = request.getFontSize() != null ? request.getFontSize() : 28f;

        // 估算文字块宽高（用于定位/居中）
        PDFont drawFont = font != null ? font : new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
        float textWidth = drawFont.getStringWidth(text) / 1000f * fontSize;
        float textHeight = fontSize * LINE_HEIGHT_FACTOR;

        // 计算对齐锚点 + 偏移
        float[] anchor = computeAnchor(pageWidth, pageHeight, textWidth, textHeight, request);
        float cx = anchor[0] + textWidth / 2f;
        float cy = anchor[1] + textHeight / 2f;

        double angle = request.getAngle() != null ? request.getAngle() : 0d;
        double opacity = clampOpacity(request.getOpacity());

        Color color = parseColor(request.getColor());

        try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

            // 透明度（非描边 = 填充/文字）
            PDExtendedGraphicsState ext = new PDExtendedGraphicsState();
            ext.setNonStrokingAlphaConstant((float) opacity);
            ext.setAlphaSourceFlag(true);
            cs.setGraphicsStateParameters(ext);

            cs.setNonStrokingColor(color);
            cs.beginText();
            cs.setFont(drawFont, fontSize);

            // 绕文字块中心旋转 + 居中（直接构造矩阵，避免 multiply 合成顺序问题）
            double rad = Math.toRadians(angle);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            Matrix transform = new Matrix(cos, sin, -sin, cos,
                    cx - cos * textWidth / 2f + sin * textHeight / 2f,
                    cy - sin * textWidth / 2f - cos * textHeight / 2f);
            cs.setTextMatrix(transform);
            cs.showText(text);
            cs.endText();
        }

        LOGGER.debug("[WatermarkRenderer#renderText] text={}, font={}, size={}, angle={}, opacity={}, color={}",
                text, request.getFont(), fontSize, angle, opacity, color);
    }

    /**
     * 在页面上绘制图片水印
     * <p>
     * 尺寸双轨：默认宽度 = 页面宽度 × ratio%；"固定水印比例"开启时用图片原始尺寸（1px≈1pt）。
     *
     * @param doc       当前打开的 PDF 文档
     * @param page      目标页面
     * @param request   水印配置
     * @param imageBytes 水印图片字节（PNG/JPG/GIF/BMP）
     * @throws IOException 解码或绘制失败
     */
    public static void renderImage(PDDocument doc, PDPage page, WatermarkRequest request, byte[] imageBytes)
            throws IOException {
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (bi == null) {
            throw new IOException("cannot decode watermark image");
        }
        int iw = bi.getWidth();
        int ih = bi.getHeight();

        PDRectangle media = page.getMediaBox();
        float pageWidth = media.getWidth();
        float pageHeight = media.getHeight();

        boolean fixed = request.getFixedRatio() != null && request.getFixedRatio();
        float wmW;
        float wmH;
        if (fixed) {
            wmW = iw;
            wmH = ih;
        } else {
            float ratio = request.getRatio() != null ? (float) (request.getRatio() / 100.0) : 0.5f;
            wmW = pageWidth * ratio;
            wmH = wmW * ih / (float) iw;
        }

        float[] anchor = computeAnchor(pageWidth, pageHeight, wmW, wmH, request);
        float cx = anchor[0] + wmW / 2f;
        float cy = anchor[1] + wmH / 2f;

        double angle = request.getAngle() != null ? request.getAngle() : 0d;
        double opacity = clampOpacity(request.getOpacity());

        PDImageXObject img = LosslessFactory.createFromImage(doc, bi);
        try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

            PDExtendedGraphicsState ext = new PDExtendedGraphicsState();
            ext.setNonStrokingAlphaConstant((float) opacity);
            ext.setAlphaSourceFlag(true);
            cs.setGraphicsStateParameters(ext);

            // Do 绘制图片是填充单位正方形 [0,1]x[0,1]，故线性缩放到 wmW/wmH 即可。
            // 直接构造矩阵（线性=R×S、平移=center + R*(-half)），避免 multiply 合成顺序问题
            double rad = Math.toRadians(angle);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            Matrix transform = new Matrix(cos * wmW, sin * wmW, -sin * wmH, cos * wmH,
                    cx - cos * wmW / 2f + sin * wmH / 2f,
                    cy - sin * wmW / 2f - cos * wmH / 2f);
            cs.drawImage(img, transform);
        }

        LOGGER.debug("[WatermarkRenderer#renderImage] size={}x{}, wm={}x{}, angle={}, opacity={}, fixed={}",
                iw, ih, wmW, wmH, angle, opacity, fixed);
    }

    /**
     * 计算水印内容左下角锚点坐标（PDF 用户空间，y 自下而上）
     * 返回 [x, y]
     */
    private static float[] computeAnchor(float pageWidth, float pageHeight,
                                         float contentWidth, float contentHeight,
                                         WatermarkRequest request) {
        float offsetX = (float) (request.getOffsetX() != null ? request.getOffsetX() : 0d) * CM_TO_PT;
        float offsetY = (float) (request.getOffsetY() != null ? request.getOffsetY() : 0d) * CM_TO_PT;

        String alignX = request.getAlignX() != null ? request.getAlignX() : "center";
        String alignY = request.getAlignY() != null ? request.getAlignY() : "middle";

        float x;
        switch (alignX) {
            case "left" -> x = offsetX;
            case "right" -> x = pageWidth - contentWidth + offsetX;
            default -> x = (pageWidth - contentWidth) / 2f + offsetX;
        }

        float y;
        switch (alignY) {
            case "top" -> y = pageHeight - contentHeight + offsetY;
            case "bottom" -> y = offsetY;
            default -> y = (pageHeight - contentHeight) / 2f + offsetY;
        }
        return new float[]{x, y};
    }

    /**
     * 透明度夹取到 [0,1]，null 用默认 0.5
     */
    private static double clampOpacity(Double opacity) {
        double v = opacity != null ? opacity : 0.5d;
        return Math.max(0d, Math.min(1d, v));
    }

    /**
     * 解析 hex 颜色为 AWT Color，失败返回灰色
     */
    private static Color parseColor(String hex) {
        if (hex == null) {
            return Color.GRAY;
        }
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            return new Color(Integer.parseInt(clean, 16));
        } catch (Exception e) {
            LOGGER.warn("[WatermarkRenderer#parseColor] invalid color '{}', fallback to gray", hex);
            return Color.GRAY;
        }
    }
}
