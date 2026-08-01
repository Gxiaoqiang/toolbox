package com.toolbox.service.pdf.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.pdf.DewatermarkRequest;
import com.toolbox.model.pdf.DewatermarkRequest.RegionItem;
import com.toolbox.model.pdf.DewatermarkResult;
import com.toolbox.model.pdf.DewatermarkResult.RegionResult;
import com.toolbox.service.pdf.PdfDewatermarkService;
import com.toolbox.service.pdf.impl.WatermarkStreamEditor.RegionBox;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PDF 去水印服务实现 — 基于 {@link WatermarkStreamEditor} 内容流编辑
 *
 * @author toolbox
 * @since 2026-08-01
 */
@Service
public class PdfDewatermarkServiceImpl implements PdfDewatermarkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfDewatermarkServiceImpl.class);

    private static final String APPLY_ALL = "all";
    private static final String APPLY_PAGE = "page";

    @Override
    public DewatermarkResult dewatermark(byte[] pdfBytes, String originalFilename, DewatermarkRequest request) {
        // 1. 参数校验
        validateRequest(request);

        String applyTo = request.getApplyTo();
        List<RegionItem> regions = request.getRegions();

        LOGGER.info("[PdfDewatermarkServiceImpl#dewatermark] file={}, applyTo={}, regions={}",
                originalFilename, applyTo, regions.size());

        // 2. 加载 PDF 并逐页编辑
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            // 2.1 检查加密
            if (doc.isEncrypted()) {
                throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPTED);
            }

            int totalPages = doc.getNumberOfPages();
            boolean applyAll = APPLY_ALL.equals(applyTo);

            // 2.2 page 模式下校验页码范围
            if (!applyAll) {
                for (RegionItem region : regions) {
                    if (region.getPage() < 0 || region.getPage() >= totalPages) {
                        throw new BusinessException(ErrorCodeEnum.PDF_PAGE_OUT_OF_RANGE);
                    }
                }
            }

            // 每个区域是否至少在一页上成功删除内容
            boolean[] regionRemoved = new boolean[regions.size()];

            // 2.3 逐页处理：收集该页应生效的框，交给编辑器
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                PDPage page = doc.getPage(pageIndex);
                float pageHeight = page.getMediaBox().getHeight();

                List<RegionBox> boxes = new ArrayList<>();
                List<Integer> boxRegionIdx = new ArrayList<>();
                for (int ri = 0; ri < regions.size(); ri++) {
                    RegionItem region = regions.get(ri);
                    if (applyAll || region.getPage() == pageIndex) {
                        // 前端 Y 为左上角原点，翻转为 PDF 左下角坐标
                        float bx = (float) region.getX();
                        float by = pageHeight - (float) region.getY() - (float) region.getH();
                        boxes.add(new RegionBox(bx, by, (float) region.getW(), (float) region.getH()));
                        boxRegionIdx.add(ri);
                    }
                }

                if (boxes.isEmpty()) {
                    continue;
                }

                WatermarkStreamEditor editor = new WatermarkStreamEditor(boxes);
                editor.processPage(doc, page);
                boolean[] removed = editor.getRemovedFlags();
                for (int i = 0; i < removed.length; i++) {
                    if (removed[i]) {
                        regionRemoved[boxRegionIdx.get(i)] = true;
                    }
                }
            }

            // 2.4 汇总 removed / failed 区域
            List<RegionResult> removedList = new ArrayList<>();
            List<RegionResult> failedList = new ArrayList<>();
            for (int ri = 0; ri < regions.size(); ri++) {
                RegionItem region = regions.get(ri);
                RegionResult result = new RegionResult(region.getPage(), region.getX(),
                        region.getY(), region.getW(), region.getH());
                if (regionRemoved[ri]) {
                    removedList.add(result);
                } else {
                    failedList.add(result);
                }
            }

            // 2.5 保存输出并 base64 编码
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                doc.save(bos);
                DewatermarkResult result = new DewatermarkResult();
                result.setPdfBase64(Base64.getEncoder().encodeToString(bos.toByteArray()));
                result.setRemoved(removedList);
                result.setFailed(failedList);
                LOGGER.info("[PdfDewatermarkServiceImpl#dewatermark] done: file={}, totalPages={}, "
                                + "regions={}, removed={}, failed={}, resultSize={}",
                        originalFilename, totalPages, regions.size(), removedList.size(),
                        failedList.size(), bos.size());
                return result;
            }

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            LOGGER.error("[PdfDewatermarkServiceImpl#dewatermark] error: file={}", originalFilename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_DEWATERMARK_PROCESS_ERROR);
        }
    }

    /**
     * 校验去水印请求参数
     */
    private static void validateRequest(DewatermarkRequest request) {
        if (request == null || request.getRegions() == null || request.getRegions().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_DEWATERMARK_REGIONS_EMPTY);
        }
        String applyTo = request.getApplyTo();
        if (applyTo == null || (!APPLY_ALL.equals(applyTo) && !APPLY_PAGE.equals(applyTo))) {
            throw new BusinessException(ErrorCodeEnum.PDF_DEWATERMARK_APPLY_INVALID);
        }
        for (RegionItem region : request.getRegions()) {
            if (!region.isValid()) {
                throw new BusinessException(ErrorCodeEnum.PARAM_INVALID);
            }
        }
    }
}
