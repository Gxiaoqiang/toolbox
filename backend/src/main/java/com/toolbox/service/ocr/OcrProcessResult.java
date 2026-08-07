package com.toolbox.service.ocr;

/**
 * OCR 处理结果
 *
 * @param data           结果文件字节数组
 * @param mimeType       结果 MIME 类型
 * @param filename       建议下载文件名
 * @param totalPages     文档总页数
 * @param scannedPages   OCR 扫描页数
 * @param nativePages    原生文字页数
 * @param elapsedMillis  处理耗时（毫秒）
 * @author toolbox
 * @since 2026-08-04
 */
public record OcrProcessResult(
        byte[] data,
        String mimeType,
        String filename,
        int totalPages,
        int scannedPages,
        int nativePages,
        long elapsedMillis) {
}
