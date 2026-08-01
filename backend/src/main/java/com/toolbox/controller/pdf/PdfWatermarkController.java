package com.toolbox.controller.pdf;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.pdf.WatermarkRequest;
import com.toolbox.security.annotation.RateLimit;
import com.toolbox.security.ratelimit.ResourceTier;
import com.toolbox.service.pdf.PdfWatermarkService;
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

/**
 * PDF 添加水印接口
 *
 * @author toolbox
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfWatermarkController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfWatermarkController.class);

    private final PdfWatermarkService pdfWatermarkService;
    private final ObjectMapper objectMapper;

    public PdfWatermarkController(PdfWatermarkService pdfWatermarkService, ObjectMapper objectMapper) {
        this.pdfWatermarkService = pdfWatermarkService;
        this.objectMapper = objectMapper;
    }

    /**
     * PDF 添加水印：按配置在目标页追加绘制水印，返回带水印的 PDF
     *
     * @param file      PDF 文件（≤50MB）
     * @param watermark 水印配置 JSON（WatermarkRequest）
     * @param image     水印图片文件（source=image 时必传，PNG/JPG/GIF/BMP）
     */
    @PostMapping("/watermark")
    @RateLimit(permitsPerSecond = 3.0, burst = 8, tier = ResourceTier.MEDIUM)
    public ResponseEntity<?> addWatermark(
            @RequestParam("file") MultipartFile file,
            @RequestParam("watermark") String watermark,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        // 1. 文件校验
        validatePdf(file);

        String filename = file.getOriginalFilename();
        LOGGER.info("[PdfWatermarkController#addWatermark] file={}", filename);

        // 2. 解析水印配置
        WatermarkRequest request;
        try {
            request = objectMapper.readValue(watermark, WatermarkRequest.class);
        } catch (Exception e) {
            LOGGER.warn("[PdfWatermarkController#addWatermark] invalid watermark json, file={}", filename);
            throw new BusinessException(ErrorCodeEnum.PDF_WATERMARK_CONFIG_INVALID);
        }

        // 3. 图片水印校验
        byte[] imageBytes = null;
        if ("image".equals(request.getSource())) {
            imageBytes = validateImage(image);
        }

        // 4. 委托 Service 处理
        try {
            byte[] result = pdfWatermarkService.addWatermark(file.getBytes(), filename, request, imageBytes);

            ByteArrayResource resource = new ByteArrayResource(result);
            String encodedFilename = URLEncoder.encode("watermark-" + filename, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(resource);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PdfWatermarkController#addWatermark] error: file={}", filename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_WATERMARK_PROCESS_ERROR);
        }
    }

    // ======================== 私有方法 ========================

    /**
     * 校验上传的 PDF 文件
     */
    private void validatePdf(MultipartFile file) {
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
        if (!FileTypeValidator.isValidPdfMagic(FileTypeValidator.readHeader(file, 4))) {
            throw new BusinessException(ErrorCodeEnum.FILE_MAGIC_MISMATCH);
        }
    }

    /**
     * 校验水印图片，返回图片字节（允许 png/jpg/jpeg/gif/bmp）
     */
    private byte[] validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_WATERMARK_IMAGE_INVALID);
        }
        String name = image.getOriginalFilename();
        boolean extOk = name != null && (FileTypeValidator.hasExtension(name, "png")
                || FileTypeValidator.hasExtension(name, "jpg")
                || FileTypeValidator.hasExtension(name, "jpeg")
                || FileTypeValidator.hasExtension(name, "gif")
                || FileTypeValidator.hasExtension(name, "bmp"));
        boolean magicOk = FileTypeValidator.isValidImageMagic(FileTypeValidator.readHeader(image, 12));
        if (!extOk || !magicOk) {
            throw new BusinessException(ErrorCodeEnum.PDF_WATERMARK_IMAGE_INVALID);
        }
        try {
            return image.getBytes();
        } catch (Exception e) {
            LOGGER.warn("[PdfWatermarkController#addWatermark] read image failed");
            throw new BusinessException(ErrorCodeEnum.PDF_WATERMARK_IMAGE_INVALID);
        }
    }
}
