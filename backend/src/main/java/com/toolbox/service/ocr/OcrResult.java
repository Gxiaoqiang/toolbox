package com.toolbox.service.ocr;

import java.util.List;

/**
 * OCR 单页识别结果
 *
 * @param text      识别到的全部文本
 * @param wordBoxes 词级包围盒列表（用于构建可搜索 PDF 的透明文字层）
 * @author toolbox
 * @since 2026-08-04
 */
public record OcrResult(String text, List<WordBox> wordBoxes) {

    /**
     * 单个词的包围盒 — 用于可搜索 PDF 叠加
     *
     * @param text   词文本
     * @param x      左下角 X（PDF 坐标系，页面左下为原点，单位 pt）
     * @param y      左下角 Y
     * @param width  宽度
     * @param height 高度
     */
    public record WordBox(String text, float x, float y, float width, float height) {
    }
}
