package com.toolbox.controller.pdf;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.common.R;
import com.toolbox.model.pdf.DewatermarkRequest;
import com.toolbox.model.pdf.DewatermarkResult;
import com.toolbox.security.annotation.RateLimit;
import com.toolbox.security.ratelimit.ResourceTier;
import com.toolbox.service.pdf.PdfDewatermarkService;
import com.toolbox.util.FileTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * PDF 去水印接口
 *
 * @author toolbox
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfDewatermarkController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfDewatermarkController.class);

    private final PdfDewatermarkService pdfDewatermarkService;
    private final ObjectMapper objectMapper;

    public PdfDewatermarkController(PdfDewatermarkService pdfDewatermarkService, ObjectMapper objectMapper) {
        this.pdfDewatermarkService = pdfDewatermarkService;
        this.objectMapper = objectMapper;
    }

    /**
     * PDF 去水印：删除框选区域内相交的文字/图片绘制操作符，保留下方正文（矢量无损）
     * <p>
     * 返回区域级结果（removed / failed），PDF 以 base64 承载于 data.pdfBase64
     *
     * @param file       PDF 文件（≤50MB）
     * @param applyTo    应用范围: "all"(所有页) | "page"(仅指定页)
     * @param regions    JSON 区域列表，格式: [{page, x, y, w, h}, ...]
     *                   page 为 0-based，x/y/w/h 为前端左上角原点坐标（points）
     */
    @PostMapping("/dewatermark")
    @RateLimit(permitsPerSecond = 3.0, burst = 8, tier = ResourceTier.MEDIUM)
    public ResponseEntity<R<DewatermarkResult>> dewatermark(
            @RequestParam("file") MultipartFile file,
            @RequestParam("applyTo") String applyTo,
            @RequestParam("regions") String regions) {

        // 1. 文件校验
        validateFile(file);

        String filename = file.getOriginalFilename();
        LOGGER.info("[PdfDewatermarkController#dewatermark] file={}, applyTo={}", filename, applyTo);

        // 2. 解析 regions JSON
        List<DewatermarkRequest.RegionItem> regionList;
        try {
            regionList = objectMapper.readValue(regions,
                    new TypeReference<List<DewatermarkRequest.RegionItem>>() {});
        } catch (Exception e) {
            LOGGER.warn("[PdfDewatermarkController#dewatermark] invalid regions json, file={}", filename);
            throw new BusinessException(ErrorCodeEnum.PARAM_INVALID);
        }

        if (regionList == null || regionList.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_DEWATERMARK_REGIONS_EMPTY);
        }

        DewatermarkRequest request = new DewatermarkRequest(applyTo, regionList);
        LOGGER.info("[PdfDewatermarkController#dewatermark] file={}, applyTo={}, regionCount={}",
                filename, applyTo, regionList.size());

        // 3. 委托 Service 处理
        try {
            DewatermarkResult result = pdfDewatermarkService.dewatermark(file.getBytes(), filename, request);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(R.ok(result));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PdfDewatermarkController#dewatermark] error: file={}", filename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_DEWATERMARK_PROCESS_ERROR);
        }
    }

    // ======================== 私有方法 ========================

    /**
     * 校验上传文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_FILE_EMPTY);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !FileTypeValidator.hasExtension(filename, "pdf")) {
            throw new BusinessException(ErrorCodeEnum.PDF_FORMAT_INVALID);
        }
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new BusinessException(ErrorCodeEnum.DOC_FILE_TOO_LARGE);
        }
        // 魔数校验
        if (!FileTypeValidator.isValidPdfMagic(FileTypeValidator.readHeader(file, 4))) {
            throw new BusinessException(ErrorCodeEnum.FILE_MAGIC_MISMATCH);
        }
    }
}
