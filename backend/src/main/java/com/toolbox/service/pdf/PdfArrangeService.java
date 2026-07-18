package com.toolbox.service.pdf;

import com.toolbox.model.common.PdfArrangeItem;

import java.util.List;

/**
 * PDF 编排服务接口
 *
 * @author toolbox
 * @since 2026-07-18
 */
public interface PdfArrangeService {

    /**
     * 按计划重新组合多源 PDF 的页面
     *
     * @param pdfBytesList 源 PDF 文件字节数组列表
     * @param plan         编排计划（有序列表，每项为一页引用或空白页）
     * @return 编排后的 PDF 字节数组
     */
    byte[] arrange(List<byte[]> pdfBytesList, List<PdfArrangeItem> plan);
}
