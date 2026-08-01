package com.toolbox.controller.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.security.annotation.RateLimit;
import com.toolbox.security.ratelimit.ResourceTier;
import com.toolbox.service.pdf.PdfCompressConstant;
import com.toolbox.service.pdf.PdfCompressResult;
import com.toolbox.service.pdf.PdfCompressService;
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
 * PDF 压缩接口
 *
 * @author toolbox
 * @since 2026-07-14
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfCompressController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfCompressController.class);

    private final PdfCompressService pdfCompressService;

    public PdfCompressController(PdfCompressService pdfCompressService) {
        this.pdfCompressService = pdfCompressService;
    }

    /**
     * PDF 压缩：对 PDF 内嵌图片降采样并重编码以减小体积
     *
     * @param file  PDF 文件（≤50MB）
     * @param level 压缩等级 1-5，默认 3（推荐压缩）
     */
    @PostMapping("/compress")
    @RateLimit(permitsPerSecond = 2.0, burst = 5, tier = ResourceTier.MEDIUM)
    public ResponseEntity<?> compress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "level", defaultValue = "" + PdfCompressConstant.DEFAULT_LEVEL) int level) {

        // 参数校验（Controller 职责）
        validateFile(file);

        String filename = file.getOriginalFilename();
        LOGGER.info("[PdfCompressController#compress] file={}, level={}", filename, level);

        // 委托 Service 处理
        try {
            PdfCompressResult result = pdfCompressService.compress(file.getBytes(), filename, level);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodeFilename("compressed-" + filename))
                    .header("X-Original-Size", String.valueOf(result.getOriginalSize()))
                    .header("X-Compressed-Size", String.valueOf(result.getCompressedSize()))
                    .body(new ByteArrayResource(result.getData()));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PdfCompressController#compress] error: file={}", filename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_COMPRESS_PROCESS_ERROR);
        }
    }

    // ======================== 私有方法 ========================

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_FILE_EMPTY);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !FileTypeValidator.hasExtension(filename, "pdf")) {
            throw new BusinessException(ErrorCodeEnum.PDF_FORMAT_INVALID);
        }
        if (file.getSize() > PdfCompressConstant.MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodeEnum.DOC_FILE_TOO_LARGE);
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
