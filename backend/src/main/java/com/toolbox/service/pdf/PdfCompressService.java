package com.toolbox.service.pdf;

/**
 * PDF 压缩服务接口
 *
 * @author toolbox
 * @since 2026-07-14
 */
public interface PdfCompressService {

    /**
     * 压缩 PDF 文件
     *
     * @param pdfBytes         PDF 文件字节数组
     * @param originalFilename 原始文件名（用于校验）
     * @param level            压缩等级 1-5
     * @return 压缩结果（含字节、原始大小、压缩后大小）
     */
    PdfCompressResult compress(byte[] pdfBytes, String originalFilename, int level);
}
