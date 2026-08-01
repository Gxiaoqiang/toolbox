package com.toolbox.service.pdf;

import com.toolbox.model.pdf.DewatermarkRequest;
import com.toolbox.model.pdf.DewatermarkResult;

/**
 * PDF 去水印服务接口
 *
 * @author toolbox
 * @since 2026-08-01
 */
public interface PdfDewatermarkService {

    /**
     * 对 PDF 进行去水印处理：删除框内文字/图片绘制操作符，保留下方正文（矢量无损）
     *
     * @param pdfBytes         PDF 文件字节数组
     * @param originalFilename 原始文件名
     * @param request          去水印请求（applyTo + regions 列表）
     * @return 去水印结果（含 base64 PDF + removed/failed 区域）
     */
    DewatermarkResult dewatermark(byte[] pdfBytes, String originalFilename, DewatermarkRequest request);
}
