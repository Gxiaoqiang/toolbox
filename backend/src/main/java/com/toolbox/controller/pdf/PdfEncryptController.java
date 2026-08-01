package com.toolbox.controller.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.security.annotation.RateLimit;
import com.toolbox.security.ratelimit.ResourceTier;
import com.toolbox.service.pdf.PdfEncryptService;
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
 * PDF 加密接口
 *
 * @author toolbox
 * @since 2026-07-19
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfEncryptController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfEncryptController.class);

    /** 单文件大小上限 50MB */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    private final PdfEncryptService pdfEncryptService;

    public PdfEncryptController(PdfEncryptService pdfEncryptService) {
        this.pdfEncryptService = pdfEncryptService;
    }

    /**
     * PDF 加密：对 PDF 设置密码和权限保护
     *
     * @param file          PDF 文件（≤50MB）
     * @param userPassword  用户密码（打开密码）
     * @param ownerPassword 所有者密码（权限密码）
     * @param canPrint      允许打印
     * @param canCopy       允许复制/提取内容
     * @param canModify     允许修改文档内容
     * @param canAnnotate   允许编辑注释和填写表单
     * @param canAssemble   允许页面组装
     */
    @PostMapping("/encrypt")
    @RateLimit(permitsPerSecond = 3.0, burst = 8, tier = ResourceTier.MEDIUM)
    public ResponseEntity<?> encrypt(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userPassword", defaultValue = "") String userPassword,
            @RequestParam(value = "ownerPassword", defaultValue = "") String ownerPassword,
            @RequestParam(value = "canPrint", defaultValue = "true") boolean canPrint,
            @RequestParam(value = "canCopy", defaultValue = "true") boolean canCopy,
            @RequestParam(value = "canModify", defaultValue = "true") boolean canModify,
            @RequestParam(value = "canAnnotate", defaultValue = "true") boolean canAnnotate,
            @RequestParam(value = "canAssemble", defaultValue = "true") boolean canAssemble) {

        // 文件校验（Controller 职责）
        validateFile(file);

        String filename = file.getOriginalFilename();
        LOGGER.info("[PdfEncryptController#encrypt] file={}", filename);

        // 委托 Service 处理
        try {
            byte[] encrypted = pdfEncryptService.encrypt(
                    file.getBytes(), userPassword, ownerPassword,
                    canPrint, canCopy, canModify, canAnnotate, canAssemble);

            String outputName = buildOutputFilename(filename);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodeFilename(outputName))
                    .body(new ByteArrayResource(encrypted));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PdfEncryptController#encrypt] error: file={}", filename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_PROCESS_ERROR);
        }
    }

    // ======================== 私有方法 ========================

    /**
     * 校验上传文件：非空、格式、大小
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_FILE_EMPTY);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !FileTypeValidator.hasExtension(filename, "pdf")) {
            throw new BusinessException(ErrorCodeEnum.PDF_FORMAT_INVALID);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodeEnum.DOC_FILE_TOO_LARGE);
        }
        // 魔数校验
        if (!FileTypeValidator.isValidPdfMagic(FileTypeValidator.readHeader(file, 4))) {
            throw new BusinessException(ErrorCodeEnum.FILE_MAGIC_MISMATCH);
        }
    }

    /**
     * 构建输出文件名：原文件名_encrypted.pdf
     */
    private static String buildOutputFilename(String originalFilename) {
        if (originalFilename == null) {
            return "encrypted.pdf";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            return originalFilename.substring(0, dotIndex) + "_encrypted.pdf";
        }
        return originalFilename + "_encrypted.pdf";
    }

    /**
     * URL 编码文件名
     */
    private static String encodeFilename(String filename) {
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
