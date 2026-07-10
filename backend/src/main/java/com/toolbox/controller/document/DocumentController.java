package com.toolbox.controller.document;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.document.DocumentService;
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

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文档转换控制器 — 支持 doc/docx/wps 转 PDF
 *
 * @author toolbox
 * @since 2026-07-10
 */
@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentController.class);
    private static final int MAX_FILES = 5;
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/convert-to-pdf")
    public ResponseEntity<?> convertToPdf(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.DOC_FILE_EMPTY);
        }
        if (files.size() > MAX_FILES) {
            throw new BusinessException(ErrorCodeEnum.DOC_TOO_MANY_FILES);
        }

        LOGGER.info("文档转 PDF 请求: {} 个文件", files.size());

        List<String> successNames = new ArrayList<>();
        List<String> errorEntries = new ArrayList<>();

        try {
            ByteArrayOutputStream zipBos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(zipBos)) {
                for (MultipartFile file : files) {
                    String originalFilename = file.getOriginalFilename();

                    if (originalFilename == null || !FileTypeValidator.isAllowedDocument(originalFilename)) {
                        throw new BusinessException(ErrorCodeEnum.DOC_FORMAT_INVALID);
                    }
                    if (file.isEmpty()) {
                        throw new BusinessException(ErrorCodeEnum.DOC_FILE_EMPTY);
                    }
                    if (file.getSize() > 50 * 1024 * 1024) {
                        throw new BusinessException(ErrorCodeEnum.DOC_FILE_TOO_LARGE);
                    }

                    try {
                        byte[] pdfBytes = documentService.convertToPdf(file.getBytes(), originalFilename);

                        String baseName = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
                        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
                        String pdfFilename = baseName + "_" + ext + "_converted.pdf";

                        ZipEntry entry = new ZipEntry(pdfFilename);
                        zos.putNextEntry(entry);
                        zos.write(pdfBytes);
                        zos.closeEntry();
                        successNames.add(pdfFilename);
                        LOGGER.info("转换成功: {} -> {}", originalFilename, pdfFilename);

                    } catch (BusinessException e) {
                        LOGGER.warn("转换失败: {} - {}", originalFilename, e.getMessage());
                        errorEntries.add(jsonError(originalFilename, e.getMessage()));
                    } catch (Exception e) {
                        LOGGER.error("转换异常: {}", originalFilename, e);
                        errorEntries.add(jsonError(originalFilename, "转换失败"));
                    }
                }

                if (!errorEntries.isEmpty()) {
                    ZipEntry errEntry = new ZipEntry("_errors.json");
                    zos.putNextEntry(errEntry);
                    String json = "{\"failed\":[" + String.join(",", errorEntries) + "]}";
                    zos.write(json.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }

            ByteArrayResource resource = new ByteArrayResource(zipBos.toByteArray());
            String encodedFilename = URLEncoder.encode("doc-to-pdf-result.zip", StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(resource);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("批量转换异常", e);
            throw new BusinessException(ErrorCodeEnum.DOC_CONVERT_ERROR);
        }
    }

    private String jsonError(String filename, String reason) {
        String safeFilename = filename != null ? filename.replace("\"", "\\\"") : "unknown";
        String safeReason = reason.replace("\"", "\\\"");
        return "{\"filename\":\"" + safeFilename + "\",\"reason\":\"" + safeReason + "\"}";
    }
}
