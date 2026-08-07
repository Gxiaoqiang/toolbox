package com.toolbox.service.pdf.toppt;

import java.io.IOException;

/**
 * PDF 转可编辑 PPT 引擎抽象
 * <p>
 * 双实现由 {@link PdfToPptController} 按 {@code engine} 参数分发：
 * <ul>
 *   <li>{@code algorithm} — 算法还原：PDFBox 提取坐标 → 段落聚拢 → 底图 + 文本框（默认）</li>
 *   <li>{@code ai} — AI 重排：提取文本 → 大模型结构化/清洗/重排 → PPTX</li>
 * </ul>
 *
 * @author toolbox
 * @since 2026-08-05
 */
public interface PdfToPptService {

    /**
     * 引擎是否可用（如 AI 引擎需已配置大模型 API key）
     *
     * @return true 可用
     */
    boolean isAvailable();

    /**
     * 将 PDF 转换为可编辑文档
     *
     * @param pdfBytes         PDF 文件字节
     * @param originalFilename 原始文件名（用于取基础名）
     * @param format          输出格式：{@code ppt}（可编辑 PPTX）或 {@code word}（可编辑 Word）
     * @return 文档字节
     * @throws IOException 转换失败
     */
    byte[] convert(byte[] pdfBytes, String originalFilename, String format) throws IOException;
}
