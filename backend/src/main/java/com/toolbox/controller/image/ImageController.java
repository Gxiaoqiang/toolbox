package com.toolbox.controller.image;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.image.ImageToPdfService;
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
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 图片处理接口
 *
 * @author toolbox
 * @since 2026-07-19
 */
@RestController
@RequestMapping("/api/image")
public class ImageController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageController.class);

    private static final int MAX_FILES = 50;
    private static final long MAX_SINGLE_SIZE = 5 * 1024 * 1024;   // 5MB
    private static final long MAX_TOTAL_SIZE = 100 * 1024 * 1024;  // 100MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final ImageToPdfService imageToPdfService;

    public ImageController(ImageToPdfService imageToPdfService) {
        this.imageToPdfService = imageToPdfService;
    }

    /**
     * 图片转 PDF：将多张图片按配置合并为 PDF 文件
     *
     * @param files       图片文件（1-50 个，单个 ≤5MB，总计 ≤100MB）
     * @param orientation 页面方向：portrait / landscape
     * @param margin      页面边距：none / small / large
     * @param fitMode     图片适配方式：contain / cover / stretch
     * @param merge       是否合并为一个 PDF（false 时每张独立 PDF 打包 ZIP）
     */
    @PostMapping("/to-pdf")
    public ResponseEntity<?> convertToPdf(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "orientation", defaultValue = "portrait") String orientation,
            @RequestParam(value = "margin", defaultValue = "small") String margin,
            @RequestParam(value = "fitMode", defaultValue = "contain") String fitMode,
            @RequestParam(value = "merge", defaultValue = "true") boolean merge) {

        // 1. 文件数量校验
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.IMAGE_FILE_COUNT_INVALID);
        }
        if (files.size() > MAX_FILES) {
            throw new BusinessException(ErrorCodeEnum.IMAGE_FILE_COUNT_INVALID);
        }

        // 2. 逐文件校验（格式 + 大小）
        long totalSize = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new BusinessException(ErrorCodeEnum.IMAGE_FILE_COUNT_INVALID);
            }
            String filename = file.getOriginalFilename();
            if (filename == null || !isAllowedImage(filename)) {
                throw new BusinessException(ErrorCodeEnum.IMAGE_FORMAT_UNSUPPORTED);
            }
            if (file.getSize() > MAX_SINGLE_SIZE) {
                throw new BusinessException(ErrorCodeEnum.IMAGE_FILE_TOO_LARGE);
            }
            totalSize += file.getSize();
        }

        // 3. 总大小校验
        if (totalSize > MAX_TOTAL_SIZE) {
            throw new BusinessException(ErrorCodeEnum.IMAGE_TOTAL_SIZE_EXCEEDED);
        }

        LOGGER.info("[ImageController#convertToPdf] {} files, total={}MB, orientation={}, margin={}, fitMode={}, merge={}",
                files.size(), totalSize / (1024 * 1024), orientation, margin, fitMode, merge);

        try {
            // 4. 读取文件字节和扩展名
            List<byte[]> imageBytesList = new ArrayList<>(files.size());
            List<String> extensions = new ArrayList<>(files.size());
            for (MultipartFile file : files) {
                imageBytesList.add(file.getBytes());
                extensions.add(getExtension(file.getOriginalFilename()));
            }

            if (merge) {
                // 5a. 合并模式：所有图片 → 单个 PDF
                byte[] pdfBytes = imageToPdfService.convertToPdf(
                        imageBytesList, extensions, orientation, margin, fitMode);

                String encodedFilename = URLEncoder.encode("images.pdf", StandardCharsets.UTF_8)
                        .replace("+", "%20");

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/pdf"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename*=UTF-8''" + encodedFilename)
                        .body(new ByteArrayResource(pdfBytes));

            } else {
                // 5b. 独立模式：每张图片 → 独立 PDF → 打包 ZIP
                byte[] zipBytes = buildZipOfPdfs(imageBytesList, extensions,
                        orientation, margin, fitMode, files);

                String encodedFilename = URLEncoder.encode("images.zip", StandardCharsets.UTF_8)
                        .replace("+", "%20");

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/zip"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename*=UTF-8''" + encodedFilename)
                        .body(new ByteArrayResource(zipBytes));
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[ImageController#convertToPdf] process error", e);
            throw new BusinessException(ErrorCodeEnum.IMAGE_TO_PDF_PROCESS_ERROR);
        }
    }

    /**
     * 每张图片独立转 PDF 后打包为 ZIP
     */
    private byte[] buildZipOfPdfs(List<byte[]> imageBytesList, List<String> extensions,
                                   String orientation, String margin, String fitMode,
                                   List<MultipartFile> files) throws Exception {
        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
            for (int i = 0; i < imageBytesList.size(); i++) {
                // 单张图片转 PDF
                byte[] pdfBytes = imageToPdfService.convertToPdf(
                        List.of(imageBytesList.get(i)),
                        List.of(extensions.get(i)),
                        orientation, margin, fitMode);

                // 生成 ZIP 条目名（用原文件名替换扩展名为 .pdf）
                String originalName = files.get(i).getOriginalFilename();
                String pdfName = replaceExtension(originalName, "pdf");

                zos.putNextEntry(new ZipEntry(pdfName));
                zos.write(pdfBytes);
                zos.closeEntry();
            }
        }
        return zipOut.toByteArray();
    }

    /** 校验文件名是否为允许的图片格式 */
    private boolean isAllowedImage(String filename) {
        String ext = extractExtension(filename);
        return ALLOWED_EXTENSIONS.contains(ext);
    }

    /** 提取扩展名（不含点号，小写） */
    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "";
        return filename.substring(dot + 1).toLowerCase();
    }

    /** 获取扩展名（含点号） */
    private String getExtension(String filename) {
        String ext = extractExtension(filename);
        return ext.isEmpty() ? "" : "." + ext;
    }

    /** 替换文件扩展名 */
    private String replaceExtension(String filename, String newExt) {
        if (filename == null) return "file." + newExt;
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        return base + "." + newExt;
    }
}
