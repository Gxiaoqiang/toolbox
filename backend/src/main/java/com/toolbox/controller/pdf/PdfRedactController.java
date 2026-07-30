package com.toolbox.controller.pdf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.pdf.RedactRequest;
import com.toolbox.service.pdf.PdfRedactService;
import com.toolbox.util.FileTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * PDF 涂黑遮盖接口
 *
 * @author toolbox
 * @since 2026-07-30
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfRedactController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfRedactController.class);

    private final PdfRedactService pdfRedactService;
    private final ObjectMapper objectMapper;

    public PdfRedactController(PdfRedactService pdfRedactService, ObjectMapper objectMapper) {
        this.pdfRedactService = pdfRedactService;
        this.objectMapper = objectMapper;
    }

    /**
     * PDF 涂黑遮盖：在指定区域绘制不透明矩形以遮盖敏感内容
     * 支持 standard（标准遮盖，内容流覆盖）和 deep（深度遮盖，页面转图片）两种模式
     *
     * @param file     PDF 文件（≤50MB）
     * @param mode     遮盖模式: "standard" | "deep"
     * @param rectsJson 矩形列表 JSON，格式: [{page, x, y, w, h, color}, ...]
     *                  page 为 0-based，x/y/w/h 为 PDF 坐标（points）
     */
    @PostMapping("/redact")
    public ResponseEntity<?> redact(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mode") String mode,
            @RequestParam("rects") String rectsJson) {

        // 1. 文件校验
        validateFile(file);

        String filename = file.getOriginalFilename();
        LOGGER.info("[PdfRedactController#redact] file={}, mode={}", filename, mode);

        // 2. 解析 rects JSON
        List<RedactRequest.RectItem> rects;
        try {
            rects = objectMapper.readValue(rectsJson,
                    new TypeReference<List<RedactRequest.RectItem>>() {});
        } catch (Exception e) {
            LOGGER.warn("[PdfRedactController#redact] invalid rects json, file={}", filename);
            throw new BusinessException(ErrorCodeEnum.PDF_REDACT_RECTS_FORMAT_ERROR);
        }

        if (rects == null || rects.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_REDACT_RECTS_EMPTY);
        }

        RedactRequest request = new RedactRequest(mode, rects);
        LOGGER.info("[PdfRedactController#redact] file={}, mode={}, rectCount={}",
                filename, mode, rects.size());

        // 3. 委托 Service 处理
        try {
            byte[] result = pdfRedactService.redact(file.getBytes(), filename, request);

            ByteArrayResource resource = new ByteArrayResource(result);
            String encodedFilename = URLEncoder.encode("redacted-" + filename, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(resource);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PdfRedactController#redact] error: file={}", filename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_REDACT_PROCESS_ERROR);
        }
    }

    /**
     * 渲染 PDF 单页为 PNG 图片（解决 pdfjs-dist 无法渲染复杂 PDF 的降级方案）
     *
     * @param file     PDF 文件
     * @param pageIndex 页码（0-based）
     * @param dpi      渲染 DPI，默认 150
     */
    @PostMapping("/render-page")
    public ResponseEntity<?> renderPage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("pageIndex") int pageIndex,
            @RequestParam(value = "dpi", defaultValue = "150") int dpi) {

        validateFile(file);

        if (dpi < 72 || dpi > 300) {
            dpi = 150;
        }

        String filename = file.getOriginalFilename();
        LOGGER.info("[PdfRedactController#renderPage] file={}, pageIndex={}, dpi={}", filename, pageIndex, dpi);

        try {
            byte[] pngBytes = pdfRedactService.renderPage(file.getBytes(), pageIndex, dpi);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(new ByteArrayResource(pngBytes));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PdfRedactController#renderPage] error: file={}", filename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_REDACT_PROCESS_ERROR);
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
    }
}
