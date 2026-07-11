package com.toolbox.controller.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.common.R;
import com.toolbox.service.pdf.PdfService;
import com.toolbox.util.FileTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 处理接口
 *
 * @author toolbox
 * @since 2026-07-10
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfController.class);
    private final PdfService pdfService;

    public PdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    /**
     * PDF 切分：支持逐页拆分、按页码范围、每 N 页拆分，返回 ZIP 下载
     */
    @PostMapping("/split")
    public ResponseEntity<?> splitPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mode") String mode,
            @RequestParam(value = "pages", required = false) String pages,
            @RequestParam(value = "everyN", defaultValue = "0") int everyN,
            @RequestParam(value = "preserveMeta", defaultValue = "false") boolean preserveMeta) {

        // 文件非空校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_FILE_EMPTY);
        }

        // 文件类型校验（通过扩展名）
        String filename = file.getOriginalFilename();
        if (filename == null || !FileTypeValidator.hasExtension(filename, "pdf")) {
            throw new BusinessException(ErrorCodeEnum.PDF_FORMAT_INVALID);
        }

        LOGGER.info("PDF 切分请求: file={}, size={}, mode={}",
                file.getOriginalFilename(), file.getSize(), mode);

        try {
            byte[] zipBytes = pdfService.splitPdf(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    mode,
                    pages,
                    everyN,
                    preserveMeta
            );

            ByteArrayResource resource = new ByteArrayResource(zipBytes);
            String encodedFilename = URLEncoder.encode("pdf-split-result.zip", StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(resource);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("PDF 切分异常: file={}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCodeEnum.PDF_PROCESS_ERROR);
        }
    }

    /**
     * PDF 合并：将多个 PDF 文件按顺序合并为一个 PDF
     */
    @PostMapping("/merge")
    public ResponseEntity<?> mergePdf(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "preserveMeta", defaultValue = "false") boolean preserveMeta) {

        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PDF_MERGE_TOO_FEW);
        }
        if (files.size() == 1) {
            throw new BusinessException(ErrorCodeEnum.PDF_MERGE_TOO_FEW);
        }
        if (files.size() > 10) {
            throw new BusinessException(ErrorCodeEnum.PDF_MERGE_TOO_MANY);
        }

        // 格式和大小校验
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new BusinessException(ErrorCodeEnum.PDF_FILE_EMPTY);
            }
            String filename = file.getOriginalFilename();
            if (filename == null || !FileTypeValidator.hasExtension(filename, "pdf")) {
                throw new BusinessException(ErrorCodeEnum.PDF_FORMAT_INVALID);
            }
            if (file.getSize() > 5 * 1024 * 1024) {
                throw new BusinessException(ErrorCodeEnum.DOC_FILE_TOO_LARGE);
            }
        }

        LOGGER.info("PDF 合并请求: {} 个文件", files.size());

        try {
            List<byte[]> pdfBytesList = new ArrayList<>();
            for (MultipartFile file : files) {
                pdfBytesList.add(file.getBytes());
            }

            byte[] mergedBytes = pdfService.mergePdf(pdfBytesList, preserveMeta);

            ByteArrayResource resource = new ByteArrayResource(mergedBytes);
            String encodedFilename = URLEncoder.encode("merged.pdf", StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/pdf"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(resource);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("PDF 合并异常", e);
            throw new BusinessException(ErrorCodeEnum.PDF_PROCESS_ERROR);
        }
    }
}
