package com.toolbox.service.document;

/**
 * 文档转换服务接口
 *
 * @author toolbox
 * @since 2026-07-10
 */
public interface DocumentService {

    /**
     * 将单个文档转换为 PDF
     *
     * @param fileBytes       文档文件字节数组
     * @param originalFilename 原始文件名
     * @return PDF 字节数组
     */
    byte[] convertToPdf(byte[] fileBytes, String originalFilename);

    /**
     * 检查 LibreOffice 是否可用
     *
     * @return true 如果 soffice 命令可执行
     */
    boolean isServiceAvailable();
}
