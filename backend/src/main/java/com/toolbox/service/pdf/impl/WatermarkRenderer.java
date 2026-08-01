package com.toolbox.service.pdf.impl;

import java.awt.Color;
import java.io.IOException;

import com.toolbox.model.pdf.WatermarkRequest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
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

            // 绕文字块中心旋转 + 居中：A = T(center) × R(θ) × T(-half)
            Matrix rot = rotationMatrix(angle);
            Matrix transform = new Matrix(1, 0, 0, 1, cx, cy)
                    .multiply(rot)
                    .multiply(new Matrix(1, 0, 0, 1, -textWidth / 2f, -textHeight / 2f));
            cs.setTextMatrix(transform);
            cs.showText(text);
            cs.endText();
        }

        LOGGER.debug("[WatermarkRenderer#renderText] text={}, font={}, size={}, angle={}, opacity={}, color={}",
                text, request.getFont(), fontSize, angle, opacity, color);
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
     * 构建旋转矩阵（PDF 用户空间逆时针）
     */
    private static Matrix rotationMatrix(double angleDeg) {
        double rad = Math.toRadians(angleDeg);
        return new Matrix((float) Math.cos(rad), (float) Math.sin(rad),
                (float) -Math.sin(rad), (float) Math.cos(rad), 0, 0);
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
