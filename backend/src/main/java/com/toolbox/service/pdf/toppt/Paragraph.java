package com.toolbox.service.pdf.toppt;

/**
 * 段落级文本框 — 版面重建后的一个可编辑文本块
 * <p>
 * 坐标采用与 {@link WordBox} 相同的「顶部原点、y 向下」约定（由 PdfTextExtractor 归一化）。
 *
 * @author toolbox
 * @since 2026-08-05
 * @param text     段落文本
 * @param x        左边界
 * @param y        底边界
 * @param width    宽
 * @param height   高
 * @param fontSize 主字号（pt）
 */
public record Paragraph(String text, float x, float y, float width, float height, float fontSize) {
}
