package com.toolbox.service.pdf.toppt;

/**
 * PDF 提取的字级坐标框
 * <p>
 * 坐标由 {@code PdfTextExtractor} 统一归一化为「顶部原点、y 向下」
 * （y_down = pageHeight - y_up - height），与 PPT/屏幕坐标一致。
 *
 * @author toolbox
 * @since 2026-08-05
 * @param x      左边界
 * @param y      底边界（PDF y 向上）
 * @param width  宽
 * @param height 高（行高）
 * @param text   字符内容
 */
public record WordBox(float x, float y, float width, float height, String text) {
}
