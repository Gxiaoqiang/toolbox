package com.toolbox.controller.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.security.annotation.RateLimit;
import com.toolbox.security.ratelimit.ResourceTier;
import com.toolbox.service.ocr.OcrProcessResult;
import com.toolbox.service.ocr.OcrService;
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

/**
 * PDF OCR 识别接口
 *
 * @author toolbox
 * @since 2026-08-04
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfOcrController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfOcrController.class);

    /** 文件大小上限 */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private final OcrService ocrService;

    public PdfOcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    /**
     * PDF OCR 识别：混合 PDF 智能检测，输出可搜索 PDF / 纯文本 / Markdown / Excel
     *
     * @param file     PDF 文件（≤50MB）
     * @param format   输出格式（searchable_pdf / text / md / xlsx，默认 searchable_pdf）
     * @param language 识别语言（chi_sim / eng / chi_sim+eng，默认 chi_sim+eng）
     */
    @PostMapping("/ocr")
    @RateLimit(permitsPerSecond = 1.0, burst = 2, tier = ResourceTier.HEAVY)
    public ResponseEntity<?> ocr(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "searchable_pdf") String format,
            @RequestParam(value = "language", defaultValue = "chi_sim+eng") String language) {

        // 参数校验（Controller 职责）
        validateFile(file);

        String filename = file.getOriginalFilename();
        LOGGER.info("[PdfOcrController#ocr] file={}, format={}, language={}", filename, format, language);

        try {
            OcrProcessResult result = ocrService.process(file.getBytes(), filename, format, language);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(result.mimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodeFilename(result.filename()))
                    .header("X-Total-Pages", String.valueOf(result.totalPages()))
                    .header("X-Scanned-Pages", String.valueOf(result.scannedPages()))
                    .header("X-Native-Pages", String.valueOf(result.nativePages()))
                    .header("X-Ocr-Time-Ms", String.valueOf(result.elapsedMillis()))
                    .body(new ByteArrayResource(result.data()));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PdfOcrController#ocr] error: file={}", filename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_OCR_PROCESS_ERROR);
        }
    }

    // ======================== 私有方法 ========================

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_OCR_FILE_EMPTY);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !FileTypeValidator.hasExtension(filename, "pdf")) {
            throw new BusinessException(ErrorCodeEnum.PDF_OCR_FORMAT_INVALID);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodeEnum.PDF_OCR_FILE_TOO_LARGE);
        }
        // 魔数校验：防止将非 PDF 文件改扩展名上传
        if (!FileTypeValidator.isValidPdfMagic(FileTypeValidator.readHeader(file, 4))) {
            throw new BusinessException(ErrorCodeEnum.FILE_MAGIC_MISMATCH);
        }
    }

    private static String encodeFilename(String filename) {
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
