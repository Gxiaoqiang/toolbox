package com.toolbox.service.pdf;

/**
 * PDF 转图片结果 DTO
 *
 * @author toolbox
 * @since 2026-07-14
 */
public class PdfToImageResult {

    /** 文件字节数组（单页图片 或 ZIP 包） */
    private final byte[] data;

    /** 响应 Content-Type（如 image/png, image/jpeg, application/zip） */
    private final String contentType;

    /** 下载时的文件名 */
    private final String downloadFilename;

    public PdfToImageResult(byte[] data, String contentType, String downloadFilename) {
        this.data = data;
        this.contentType = contentType;
        this.downloadFilename = downloadFilename;
    }

    public byte[] getData() { return data; }

    public String getContentType() { return contentType; }

    public String getDownloadFilename() { return downloadFilename; }
}
