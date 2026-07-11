package com.toolbox.service.pdf;

/**
 * PDF 处理服务接口
 *
 * @author toolbox
 * @since 2026-07-10
 */
public interface PdfService {

    /**
     * 拆分 PDF 文件
     *
     * @param pdfBytes         PDF 文件字节数组
     * @param originalFilename 原始文件名（用于生成拆分后的文件名）
     * @param mode             拆分模式: "by-page" | "by-range" | "by-n"
     * @param pages            页码范围（mode=by-range 时使用）
     * @param everyN           每 N 页拆分（mode=by-n 时使用）
     * @param preserveMeta     是否保留原始 PDF 元数据
     * @return ZIP 文件字节数组
     */
    byte[] splitPdf(byte[] pdfBytes, String originalFilename, String mode,
                    String pages, int everyN, boolean preserveMeta);

    /**
     * 合并多个 PDF 文件为一个
     *
     * @param pdfBytesList   PDF 文件字节数组列表（顺序即合并顺序）
     * @param preserveMeta   是否保留第一个文件的元数据
     * @return 合并后的 PDF 字节数组
     */
    byte[] mergePdf(java.util.List<byte[]> pdfBytesList, boolean preserveMeta);
}
