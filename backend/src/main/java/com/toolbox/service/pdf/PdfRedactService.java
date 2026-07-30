package com.toolbox.service.pdf;

import com.toolbox.model.pdf.RedactRequest;

/**
 * PDF 涂黑遮盖服务接口
 *
 * @author toolbox
 * @since 2026-07-30
 */
public interface PdfRedactService {

    /**
     * 对 PDF 进行涂黑遮盖处理
     *
     * @param pdfBytes         PDF 文件字节数组
     * @param originalFilename 原始文件名
     * @param request          遮盖请求（mode + rects 列表）
     * @return 处理后的 PDF 字节数组
     */
    byte[] redact(byte[] pdfBytes, String originalFilename, RedactRequest request);
}
