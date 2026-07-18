package com.toolbox.service.pdf.impl;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.common.PdfArrangeItem;
import com.toolbox.service.pdf.PdfArrangeService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 编排服务实现——基于 Apache PDFBox 完成页面重组
 *
 * @author toolbox
 * @since 2026-07-18
 */
public class PdfArrangeServiceImpl implements PdfArrangeService {

    private static final Logger log = LoggerFactory.getLogger(PdfArrangeServiceImpl.class);

    @Override
    public byte[] arrange(List<byte[]> pdfBytesList, List<PdfArrangeItem> plan) {
        // 1. 校验计划
        validatePlan(plan, pdfBytesList.size());

        log.info("[PdfArrangeServiceImpl#arrange] processing {} source files, plan size={}",
                pdfBytesList.size(), plan.size());

        // 2. 加载所有源 PDF
        List<PDDocument> sourceDocs = new ArrayList<>(pdfBytesList.size());
        List<Integer> pageCounts = new ArrayList<>(pdfBytesList.size());
        try {
            for (byte[] bytes : pdfBytesList) {
                try {
                    PDDocument doc = Loader.loadPDF(bytes);
                    sourceDocs.add(doc);
                    pageCounts.add(doc.getNumberOfPages());
                } catch (Exception e) {
                    closeDocuments(sourceDocs);
                    log.error("[PdfArrangeServiceImpl#arrange] failed to load source PDF", e);
                    throw new BusinessException(ErrorCodeEnum.PDF_ARRANGE_PROCESS_ERROR);
                }
            }

            // 3. 二次校验——核对引用页码范围
            validatePageRanges(plan, pageCounts);

            // 4. 执行编排
            PDDocument outputDoc = new PDDocument();
            PDRectangle lastPageSize = PDRectangle.A4;

            for (PdfArrangeItem item : plan) {
                PDPage newPage;

                if (Boolean.TRUE.equals(item.blank())) {
                    // 4a. 插入空白页
                    PDRectangle size = resolvePageSize(item, lastPageSize);
                    newPage = new PDPage(size);
                } else {
                    // 4b. 从源文件导入页面（importPage 自动加入 outputDoc）
                    PDDocument srcDoc = sourceDocs.get(item.file());
                    PDPage srcPage = srcDoc.getPage(item.page() - 1);
                    newPage = outputDoc.importPage(srcPage);
                    // 跟踪页面尺寸
                    lastPageSize = srcPage.getMediaBox();
                }

                // 4c. 设置旋转
                int rotation = item.rotate() != null ? item.rotate() : 0;
                if (rotation != 0) {
                    newPage.setRotation(rotation);
                }

                if (Boolean.TRUE.equals(item.blank())) {
                    outputDoc.addPage(newPage);
                }
                // else: importPage 已将页面加入文档
            }

            // 5. 导出
            int outputPages = outputDoc.getNumberOfPages();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            outputDoc.save(out);
            outputDoc.close();

            log.info("[PdfArrangeServiceImpl#arrange] output {} pages, {} bytes",
                    outputPages, out.size());
            return out.toByteArray();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[PdfArrangeServiceImpl#arrange] process failed", e);
            throw new BusinessException(ErrorCodeEnum.PDF_ARRANGE_PROCESS_ERROR);
        } finally {
            closeDocuments(sourceDocs);
        }
    }

    /** 关闭所有打开的 PDDocument */
    private void closeDocuments(List<PDDocument> docs) {
        for (PDDocument doc : docs) {
            try {
                if (doc != null) doc.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
        }
    }

    /** 基础结构校验 */
    private void validatePlan(List<PdfArrangeItem> plan, int fileCount) {
        if (plan == null || plan.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_ARRANGE_PLAN_EMPTY);
        }
        if (plan.size() > 300) {
            throw new BusinessException(ErrorCodeEnum.PDF_ARRANGE_PLAN_TOO_LARGE);
        }
        for (int i = 0; i < plan.size(); i++) {
            PdfArrangeItem item = plan.get(i);
            // 空白页校验
            if (Boolean.TRUE.equals(item.blank())) {
                if (item.width() != null && item.width() <= 0) {
                    throw new BusinessException(ErrorCodeEnum.PARAM_INVALID);
                }
                if (item.height() != null && item.height() <= 0) {
                    throw new BusinessException(ErrorCodeEnum.PARAM_INVALID);
                }
                continue;
            }
            // 源文件页面校验
            if (item.file() == null || item.file() < 0 || item.file() >= fileCount) {
                throw new BusinessException(ErrorCodeEnum.PDF_ARRANGE_PLAN_FILE_INDEX_INVALID);
            }
            if (item.page() == null || item.page() < 1) {
                throw new BusinessException(ErrorCodeEnum.PDF_ARRANGE_PLAN_PAGE_OUT_OF_RANGE);
            }
            int rot = item.rotate() != null ? item.rotate() : 0;
            if (rot != 0 && rot != 90 && rot != 180 && rot != 270) {
                throw new BusinessException(ErrorCodeEnum.PDF_ARRANGE_PLAN_ROTATE_INVALID);
            }
        }
    }

    /** 对所有源文件页面进行范围校验 */
    private void validatePageRanges(List<PdfArrangeItem> plan, List<Integer> pageCounts) {
        for (PdfArrangeItem item : plan) {
            if (Boolean.TRUE.equals(item.blank())) continue;
            int actual = pageCounts.get(item.file());
            if (item.page() > actual) {
                throw new BusinessException(ErrorCodeEnum.PDF_ARRANGE_PLAN_PAGE_OUT_OF_RANGE);
            }
        }
    }

    /** 解析空白页尺寸: 指定尺寸优先，否则跟随前一页/退化 A4 */
    private PDRectangle resolvePageSize(PdfArrangeItem item, PDRectangle lastPageSize) {
        if (item.width() != null && item.height() != null) {
            return new PDRectangle(item.width(), item.height());
        }
        return lastPageSize;
    }
}
