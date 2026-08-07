package com.toolbox.service.ocr;

/**
 * PDF OCR 识别服务接口
 *
 * @author toolbox
 * @since 2026-08-04
 */
public interface OcrService {

    /**
     * 对 PDF 执行 OCR 识别并生成指定格式的结果文件
     * <p>
     * 混合 PDF 智能检测：原生文字页直接提取文本，扫描页执行 OCR。
     *
     * @param pdfBytes         PDF 文件字节数组
     * @param originalFilename 原始文件名（用于生成结果文件名）
     * @param format           输出格式：searchable_pdf / text / md / xlsx
     * @param language         识别语言：chi_sim / eng / chi_sim+eng
     * @return 处理结果（含字节、MIME 类型、建议文件名、统计信息）
     */
    OcrProcessResult process(byte[] pdfBytes, String originalFilename, String format, String language);
}
