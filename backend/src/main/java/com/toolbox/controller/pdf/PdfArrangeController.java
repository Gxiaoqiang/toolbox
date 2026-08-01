package com.toolbox.controller.pdf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.common.PdfArrangeItem;
import com.toolbox.security.annotation.RateLimit;
import com.toolbox.security.ratelimit.ResourceTier;
import com.toolbox.service.pdf.PdfArrangeService;
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
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 编排接口
 *
 * @author toolbox
 * @since 2026-07-18
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfArrangeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfArrangeController.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE = 45 * 1024 * 1024;
    private static final int MAX_FILES = 10;

    private final PdfArrangeService pdfArrangeService;
    private final ObjectMapper objectMapper;

    public PdfArrangeController(PdfArrangeService pdfArrangeService, ObjectMapper objectMapper) {
        this.pdfArrangeService = pdfArrangeService;
        this.objectMapper = objectMapper;
    }

    /**
     * PDF 编排：将多个 PDF 的页面按计划重组为一个 PDF
     *
     * @param files 源 PDF 文件（1-10 个，单文件 ≤10MB）
     * @param plan  编排计划 JSON 字符串
     */
    @PostMapping("/arrange")
    @RateLimit(permitsPerSecond = 2.0, burst = 5, tier = ResourceTier.MEDIUM)
    public ResponseEntity<?> arrange(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("plan") String plan) {

        // 1. 文件校验
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_FILE_EMPTY);
        }
        if (files.size() > MAX_FILES) {
            throw new BusinessException(ErrorCodeEnum.DOC_TOO_MANY_FILES);
        }

        long totalSize = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new BusinessException(ErrorCodeEnum.PDF_FILE_EMPTY);
            }
            String filename = file.getOriginalFilename();
            if (filename == null || !FileTypeValidator.hasExtension(filename, "pdf")) {
                throw new BusinessException(ErrorCodeEnum.PDF_FORMAT_INVALID);
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCodeEnum.DOC_FILE_TOO_LARGE);
            }
            if (!FileTypeValidator.isValidPdfMagic(FileTypeValidator.readHeader(file, 4))) {
                throw new BusinessException(ErrorCodeEnum.FILE_MAGIC_MISMATCH);
            }
            totalSize += file.getSize();
        }

        if (totalSize > MAX_TOTAL_SIZE) {
            throw new BusinessException(ErrorCodeEnum.DOC_FILE_TOO_LARGE);
        }

        // 2. plan 校验
        if (plan == null || plan.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_ARRANGE_PLAN_EMPTY);
        }

        LOGGER.info("[PdfArrangeController#arrange] {} files, total {} bytes",
                files.size(), totalSize);

        try {
            // 3. 解析 plan JSON
            List<PdfArrangeItem> planItems = objectMapper.readValue(
                    plan, new TypeReference<List<PdfArrangeItem>>() {});

            // 4. 读取所有文件字节
            List<byte[]> pdfBytesList = new ArrayList<>(files.size());
            for (MultipartFile file : files) {
                pdfBytesList.add(file.getBytes());
            }

            // 5. 委托 Service
            byte[] result = pdfArrangeService.arrange(pdfBytesList, planItems);

            ByteArrayResource resource = new ByteArrayResource(result);
            String encodedFilename = URLEncoder.encode("arranged.pdf", StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/pdf"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(resource);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PdfArrangeController#arrange] process error", e);
            throw new BusinessException(ErrorCodeEnum.PDF_ARRANGE_PROCESS_ERROR);
        }
    }
}
