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

    /**
     * 渲染 PDF 单页为 PNG 图片（用于前端 pdfjs-dist 无法渲染时降级预览）
     *
     * @param pdfBytes  PDF 文件字节数组
     * @param pageIndex 页码（0-based）
     * @param dpi       渲染 DPI（建议 150）
     * @return PNG 图片字节数组
     */
    byte[] renderPage(byte[] pdfBytes, int pageIndex, int dpi);
}
