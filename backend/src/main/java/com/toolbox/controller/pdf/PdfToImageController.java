package com.toolbox.controller.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.security.annotation.RateLimit;
import com.toolbox.security.ratelimit.ResourceTier;
import com.toolbox.service.pdf.ImageConvertConstant;
import com.toolbox.service.pdf.PdfToImageResult;
import com.toolbox.service.pdf.PdfToImageService;
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
 * PDF 转图片接口
 *
 * @author toolbox
 * @since 2026-07-14
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfToImageController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfToImageController.class);

    private final PdfToImageService pdfToImageService;

    public PdfToImageController(PdfToImageService pdfToImageService) {
        this.pdfToImageService = pdfToImageService;
    }

    /**
     * PDF 转图片：将 PDF 每页渲染为 PNG/JPEG
     *
     * @param file      PDF 文件（≤50MB）
     * @param dpi       输出 DPI，默认 200，范围 72-600
     * @param format    输出格式 png / jpeg，默认 png
     * @param quality   JPEG 压缩质量 0.0-1.0，默认 0.9
     * @param pageRange 页码范围（空=全部），如 "1-5" 或 "1,3,5"
     */
    @PostMapping("/to-image")
    @RateLimit(permitsPerSecond = 1.0, burst = 3, tier = ResourceTier.MEDIUM)
    public ResponseEntity<?> convertToImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dpi", defaultValue = "" + ImageConvertConstant.DEFAULT_DPI) int dpi,
            @RequestParam(value = "format", defaultValue = "png") String format,
            @RequestParam(value = "quality", defaultValue = "" + ImageConvertConstant.DEFAULT_JPEG_QUALITY) float quality,
            @RequestParam(value = "pageRange", required = false) String pageRange,
            @RequestParam(value = "trimMargin", defaultValue = "false") boolean trimMargin) {

        // 参数校验（Controller 职责）
        validateFile(file);

        String filename = file.getOriginalFilename();
        LOGGER.info("[PdfToImageController#convertToImage] file={}, dpi={}, format={}, pages={}",
                filename, dpi, format, pageRange != null ? pageRange : "all");

        // 委托 Service 处理
        try {
            PdfToImageResult result = pdfToImageService.convertToImages(
                    file.getBytes(), filename, dpi, format, quality, pageRange, trimMargin);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(result.getContentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodeFilename(result.getDownloadFilename()))
                    .body(new ByteArrayResource(result.getData()));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PdfToImageController#convertToImage] error: file={}", filename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_IMAGE_PROCESS_ERROR);
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
        if (file.getSize() > ImageConvertConstant.MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodeEnum.DOC_FILE_TOO_LARGE);
        }
        // 魔数校验
        if (!FileTypeValidator.isValidPdfMagic(FileTypeValidator.readHeader(file, 4))) {
            throw new BusinessException(ErrorCodeEnum.FILE_MAGIC_MISMATCH);
        }
    }

    private static String encodeFilename(String filename) {
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
