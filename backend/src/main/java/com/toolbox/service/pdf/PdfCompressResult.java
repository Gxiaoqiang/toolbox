package com.toolbox.service.pdf;

/**
 * PDF 压缩结果 DTO
 *
 * @author toolbox
 * @since 2026-07-14
 */
public class PdfCompressResult {

    /** 压缩后的 PDF 字节数组 */
    private final byte[] data;

    /** 原始文件大小（bytes） */
    private final long originalSize;

    /** 压缩后文件大小（bytes） */
    private final long compressedSize;

    public PdfCompressResult(byte[] data, long originalSize, long compressedSize) {
        this.data = data;
        this.originalSize = originalSize;
        this.compressedSize = compressedSize;
    }

    public byte[] getData() { return data; }

    public long getOriginalSize() { return originalSize; }

    public long getCompressedSize() { return compressedSize; }

    /**
     * 计算压缩率：0.0-1.0，表示缩小的比例
     */
    public double getCompressionRatio() {
        if (originalSize == 0) return 0;
        return 1.0 - (double) compressedSize / originalSize;
    }
}
