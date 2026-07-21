package com.toolbox.service.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PDF 服务层共享工具 — 统一的文档加载/校验，消除各 impl 中的重复模板
 *
 * @author toolbox
 * @since 2026-07-21
 */
public final class PdfSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfSupport.class);

    private PdfSupport() {}

    /**
     * 加载 PDF 并校验加密状态 — 替代各 impl 中重复的 try/catch + encrypted check。
     *
     * @param pdfBytes PDF 字节数组
     * @param context  上下文描述（用于日志，如文件名）
     * @return 已加载的非加密 PDDocument（调用方负责 close）
     * @throws BusinessException 文件损坏/加密/IO 错误
     */
    public static PDDocument loadAndValidate(byte[] pdfBytes, String context) {
        try {
            PDDocument doc = Loader.loadPDF(pdfBytes);
            if (doc.isEncrypted()) {
                try { doc.close(); } catch (Exception ignored) {}
                throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPTED);
            }
            return doc;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PdfSupport#loadAndValidate] failed: {}", context, e);
            throw new BusinessException(ErrorCodeEnum.PDF_PROCESS_ERROR);
        }
    }

    /**
     * 估计 PDF 页数（快速扫描 /Type /Page 模式，不解析全文档）
     */
    public static int estimatePageCount(byte[] pdfBytes) {
        int count = 0;
        int limit = Math.min(pdfBytes.length, 1024 * 1024);
        for (int i = 0; i < limit - 10; i++) {
            if (pdfBytes[i] == '/' && pdfBytes[i + 1] == 'T' && pdfBytes[i + 2] == 'y'
                    && pdfBytes[i + 3] == 'p' && pdfBytes[i + 4] == 'e'
                    && pdfBytes[i + 5] == ' ' && pdfBytes[i + 6] == '/'
                    && pdfBytes[i + 7] == 'P' && pdfBytes[i + 8] == 'a'
                    && pdfBytes[i + 9] == 'g' && pdfBytes[i + 10] == 'e') {
                count++;
                i += 10;
            }
        }
        return Math.max(count, 1);
    }
}
