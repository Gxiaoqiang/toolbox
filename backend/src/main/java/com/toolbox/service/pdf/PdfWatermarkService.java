package com.toolbox.service.pdf;

import com.toolbox.model.pdf.WatermarkRequest;

/**
 * PDF 添加水印服务接口
 *
 * @author toolbox
 * @since 2026-08-02
 */
public interface PdfWatermarkService {

    /**
     * 给 PDF 添加水印并返回处理后的字节
     *
     * @param pdfBytes         PDF 文件字节数组
     * @param originalFilename 原始文件名
     * @param request          水印配置
     * @param imageBytes       水印图片字节（source=image 时必传，否则可空）
     * @return 带水印的 PDF 字节数组
     */
    byte[] addWatermark(byte[] pdfBytes, String originalFilename, WatermarkRequest request, byte[] imageBytes);
}
