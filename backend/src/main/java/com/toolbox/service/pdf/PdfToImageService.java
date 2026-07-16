package com.toolbox.service.pdf;

/**
 * PDF 转图片服务接口
 *
 * @author toolbox
 * @since 2026-07-14
 */
public interface PdfToImageService {

    /**
     * 将 PDF 逐页渲染为图片
     * 单页直接返回该图片，多页自动打包为 ZIP
     *
     * @param pdfBytes          PDF 文件字节数组
     * @param originalFilename  原始文件名（用于生成输出文件名）
     * @param dpi               输出 DPI（72-600）
     * @param format            输出格式，见 {@link ImageConvertConstant.ImageFormat}
     * @param quality           JPEG 压缩质量（0.0-1.0，仅 JPEG 生效）
     * @param pageRange         页码范围：空=全部，如 "1-5" 或 "1,3,5"
     * @return 转换结果 DTO（包含二进制数据、Content-Type、文件名）
     */
    PdfToImageResult convertToImages(byte[] pdfBytes, String originalFilename,
                                     int dpi, String format, float quality, String pageRange);
}
