package com.toolbox.controller.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.security.annotation.RateLimit;
import com.toolbox.security.ratelimit.ResourceTier;
import com.toolbox.service.pdf.toppt.impl.AiPdfToPptServiceImpl;
import com.toolbox.service.pdf.toppt.impl.AlgorithmPdfToPptServiceImpl;
import com.toolbox.service.pdf.toppt.PdfToPptService;
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
 * PDF 转可编辑 PPT 控制器
 * <p>
 * 接收单个 PDF，按 {@code engine} 参数分发到算法还原或 AI 重排引擎，
 * 返回可编辑 PPTX。
 *
 * @author toolbox
 * @since 2026-08-05
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfToPptController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfToPptController.class);

    private final AlgorithmPdfToPptServiceImpl algorithmService;
    private final AiPdfToPptServiceImpl aiService;

    public PdfToPptController(AlgorithmPdfToPptServiceImpl algorithmService,
                              AiPdfToPptServiceImpl aiService) {
        this.algorithmService = algorithmService;
        this.aiService = aiService;
    }

    /**
     * PDF 转可编辑文档（PPTX / Word）
     *
     * @param file   PDF 文件（≤50MB）
     * @param engine 转换引擎：algorithm（算法还原，默认）/ ai（AI 重排）
     * @param format 输出格式：ppt（可编辑 PPTX，默认）/ word（可编辑 Word）
     * @return 可编辑文档
     */
    @PostMapping("/to-ppt")
    @RateLimit(permitsPerSecond = 1.0, burst = 2, tier = ResourceTier.HEAVY)
    public ResponseEntity<?> toPpt(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "engine", required = false, defaultValue = "algorithm") String engine,
            @RequestParam(value = "format", required = false, defaultValue = "ppt") String format) {

        validateFile(file);
        String filename = file.getOriginalFilename();
        String baseName = stripExtension(filename);
        boolean word = "word".equalsIgnoreCase(format);

        LOGGER.info("[PdfToPptController#toPpt] start, file={}, engine={}, format={}",
                filename, engine, word ? "word" : "ppt");

        boolean ai = "ai".equalsIgnoreCase(engine);
        PdfToPptService service = ai ? aiService : algorithmService;

        if (!service.isAvailable()) {
            throw new BusinessException(ErrorCodeEnum.PDF_TO_PPT_AI_UNAVAILABLE);
        }

        try {
            byte[] bytes = service.convert(file.getBytes(), filename, word ? "word" : "ppt");

            String ext = word ? "docx" : "pptx";
            String contentType = word
                    ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    : "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            String encodedFilename = URLEncoder.encode(baseName + "." + ext, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(new ByteArrayResource(bytes));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PdfToPptController#toPpt] process exception, file={}, error={}", filename, e.getMessage(), e);
            throw new BusinessException(ErrorCodeEnum.PDF_TO_PPT_PROCESS_ERROR);
        }
    }

    // ======================== 校验 ========================

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
        if (!FileTypeValidator.isValidPdfMagic(FileTypeValidator.readHeader(file, 4))) {
            throw new BusinessException(ErrorCodeEnum.FILE_MAGIC_MISMATCH);
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
