package com.toolbox.controller.ppt;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.dto.PptPreviewResponse;
import com.toolbox.model.common.R;
import com.toolbox.service.document.DocumentService;
import com.toolbox.security.annotation.RateLimit;
import com.toolbox.security.ratelimit.ResourceTier;
import com.toolbox.service.ppt.PptConvertConstant;
import com.toolbox.service.ppt.PptPdfCache;
import com.toolbox.util.FileTypeValidator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * PPT 转 PDF 控制器
 * <p>
 * 提供 PPT/PPTX 转 PDF 的预览和转换功能。
 * 预览时缓存中间 PDF，转换时直接复用，避免重复调用 LibreOffice。
 * <p>
 * 缓存策略通过 {@code toolbox.ppt.cache-type} 配置：
 * <ul>
 *   <li>{@code memory}（默认）— JVM 内存，单机部署</li>
 *   <li>{@code redis} — Redis，多实例共享</li>
 * </ul>
 *
 * @author toolbox
 * @since 2026-07-22
 */
@RestController
@RequestMapping("/api/ppt")
public class PptToPdfController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PptToPdfController.class);

    private final DocumentService documentService;
    private final PptPdfCache pdfCache;

    public PptToPdfController(DocumentService documentService, PptPdfCache pdfCache) {
        this.documentService = documentService;
        this.pdfCache = pdfCache;
    }

    /**
     * PPT 预览 — 转换为 PDF 并生成每页缩略图
     * <p>
     * 同时缓存中间 PDF，供后续 convert-to-pdf 复用。
     *
     * @param file PPT 文件（≤50MB）
     * @return 包含 cacheKey + 每页 base64 缩略图的预览数据
     */
    @PostMapping("/preview")
    public R<PptPreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        validateFile(file);
        String filename = file.getOriginalFilename();
        String baseName = stripExtension(filename);

        LOGGER.info("[PptToPdfController#preview] start, file={}", filename);

        try {
            // 1. PPT → PDF（唯一一次 LibreOffice 调用）
            byte[] pdfBytes = documentService.convertToPdf(file.getBytes(), filename);

            // 2. 生成 cacheKey 并缓存 PDF
            String cacheKey = UUID.randomUUID().toString().replace("-", "");
            pdfCache.put(cacheKey, pdfBytes, baseName);

            // 3. 渲染缩略图
            List<PptPreviewResponse.PageThumbnail> thumbnails;
            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                int totalPages = doc.getNumberOfPages();
                PDFRenderer renderer = new PDFRenderer(doc);
                thumbnails = new ArrayList<>(totalPages);

                for (int i = 0; i < totalPages; i++) {
                    BufferedImage image = renderer.renderImageWithDPI(i, PptConvertConstant.THUMBNAIL_DPI);
                    image = scaleToMaxWidth(image, PptConvertConstant.THUMBNAIL_MAX_WIDTH);
                    String base64 = imageToBase64(image, PptConvertConstant.THUMBNAIL_QUALITY);
                    thumbnails.add(new PptPreviewResponse.PageThumbnail(
                            i + 1, base64, image.getWidth(), image.getHeight()));
                }

                LOGGER.info("[PptToPdfController#preview] success, file={}, totalPages={}, cacheKey={}",
                        filename, totalPages, cacheKey);
                return R.ok(new PptPreviewResponse(totalPages, thumbnails, baseName, cacheKey));
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PptToPdfController#preview] process exception, file={}, error={}",
                    filename, e.getMessage(), e);
            throw new BusinessException(ErrorCodeEnum.PPT_PREVIEW_ERROR);
        }
    }

    /**
     * PPT 转 PDF — 按选定页面生成最终 PDF
     * <p>
     * 优先使用缓存的中间 PDF（preview 阶段生成），避免二次 LibreOffice 调用。
     * 若缓存过期则回退为重新转换。
     *
     * @param file     PPT 文件（≤50MB），缓存未命中时用于重新转换
     * @param pages    选定的页码，逗号分隔，如 "1,3,5"（空=全部）
     * @param cacheKey 预览阶段返回的缓存 key（可选，有则跳过 LibreOffice）
     * @return 最终 PDF 文件
     */
    @PostMapping("/convert-to-pdf")
    public ResponseEntity<?> convertToPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "pages", required = false, defaultValue = "") String pages,
            @RequestParam(value = "cacheKey", required = false, defaultValue = "") String cacheKey) {

        validateFile(file);
        String filename = file.getOriginalFilename();
        String baseName = stripExtension(filename);

        LOGGER.info("[PptToPdfController#convertToPdf] start, file={}, pages={}, cacheKey={}",
                filename, pages.isEmpty() ? "all" : pages,
                cacheKey.isEmpty() ? "none" : cacheKey);

        try {
            // 1. 获取中间 PDF：缓存优先，未命中则重新转换
            byte[] fullPdf = getCachedOrConvert(file, filename, cacheKey);

            // 2. 按选定页面提取
            byte[] finalPdf;
            if (pages.isBlank()) {
                finalPdf = fullPdf;
            } else {
                finalPdf = extractPages(fullPdf, pages);
            }

            // 3. 返回 PDF
            ByteArrayResource resource = new ByteArrayResource(finalPdf);
            String encodedFilename = URLEncoder.encode(baseName + ".pdf", StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(resource);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PptToPdfController#convertToPdf] process exception, file={}, error={}",
                    filename, e.getMessage(), e);
            throw new BusinessException(ErrorCodeEnum.PPT_CONVERT_ERROR);
        }
    }

    /**
     * PPT 批量转 PDF — 一次上传多个 PPT（≤10 个），支持合并或分别打包
     * <p>
     * 每个文件独立通过 LibreOffice 转换为 PDF，单个文件失败不影响其余文件。
     * <ul>
     *   <li>{@code mode=merge}：全部成功的 PDF 按上传顺序合并为一个 PDF 返回</li>
     *   <li>{@code mode=separate}（默认）：每个 PDF 单独一个 entry 打包为 ZIP 返回</li>
     * </ul>
     *
     * @param files 多个 PPT 文件（≤10 个，单个 ≤50MB）
     * @param mode  输出方式：merge 合并为单 PDF；separate 分别转换打包 ZIP
     * @return 单 PDF（merge）或 ZIP（separate）
     */
    @PostMapping("/batch-to-pdf")
    @RateLimit(permitsPerSecond = 1.0, burst = 3, tier = ResourceTier.HEAVY)
    public ResponseEntity<?> batchToPdf(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "mode", required = false, defaultValue = "separate") String mode) {

        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PPT_FILE_EMPTY);
        }
        if (files.size() > 10) {
            throw new BusinessException(ErrorCodeEnum.PPT_TOO_MANY_FILES);
        }

        LOGGER.info("[PptToPdfController#batchToPdf] start, files={}, mode={}", files.size(), mode);

        try {
            // 1. 逐文件转换，收集成功 PDF 与失败信息（单文件失败不中断）
            List<String> successNames = new ArrayList<>();
            List<byte[]> successPdfs = new ArrayList<>();
            List<String> errorEntries = new ArrayList<>();

            for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename();
                validateFile(file);

                try {
                    byte[] pdfBytes = documentService.convertToPdf(file.getBytes(), originalFilename);
                    successPdfs.add(pdfBytes);
                    successNames.add(originalFilename);
                    LOGGER.info("[PptToPdfController#batchToPdf] convert ok, file={}", originalFilename);
                } catch (BusinessException e) {
                    LOGGER.warn("[PptToPdfController#batchToPdf] convert fail, file={}, reason={}",
                            originalFilename, e.getMessage());
                    errorEntries.add(jsonError(originalFilename, e.getMessage()));
                } catch (Exception e) {
                    LOGGER.error("[PptToPdfController#batchToPdf] convert exception, file={}", originalFilename, e);
                    errorEntries.add(jsonError(originalFilename, "转换失败"));
                }
            }

            if ("merge".equalsIgnoreCase(mode)) {
                return buildMergeResponse(successPdfs, errorEntries);
            }
            return buildSeparateResponse(successPdfs, successNames, errorEntries);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PptToPdfController#batchToPdf] process exception, error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCodeEnum.PPT_BATCH_ERROR);
        }
    }

    /**
     * 将多个 PDF 合并为一个 PDF 返回
     */
    private ResponseEntity<?> buildMergeResponse(List<byte[]> pdfs, List<String> errorEntries) throws IOException {
        if (pdfs.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PPT_CONVERT_ERROR);
        }

        byte[] merged = mergePdfs(pdfs);
        if (!errorEntries.isEmpty()) {
            LOGGER.warn("[PptToPdfController#buildMergeResponse] merged with {} failed files", errorEntries.size());
        }

        ByteArrayResource resource = new ByteArrayResource(merged);
        String encodedFilename = URLEncoder.encode("ppt-merge-result.pdf", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }

    /**
     * 将多个 PDF 打包为 ZIP 返回，若有失败则附带 _errors.json
     */
    private ResponseEntity<?> buildSeparateResponse(List<byte[]> pdfs, List<String> names,
                                                    List<String> errorEntries) throws IOException {
        if (pdfs.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PPT_CONVERT_ERROR);
        }

        ByteArrayOutputStream zipBos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBos)) {
            for (int i = 0; i < pdfs.size(); i++) {
                String baseName = stripExtension(names.get(i));
                String pdfFilename = baseName + "_converted.pdf";
                ZipEntry entry = new ZipEntry(pdfFilename);
                zos.putNextEntry(entry);
                zos.write(pdfs.get(i));
                zos.closeEntry();
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
        String encodedFilename = URLEncoder.encode("ppt-to-pdf-result.zip", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }

    /**
     * 用 PDFBox 将多个 PDF 按顺序合并为一个
     */
    private byte[] mergePdfs(List<byte[]> pdfs) throws IOException {
        PDDocument outputDoc = new PDDocument();
        try {
            for (byte[] pdfBytes : pdfs) {
                try (PDDocument sourceDoc = Loader.loadPDF(pdfBytes)) {
                    for (PDPage page : sourceDoc.getPages()) {
                        outputDoc.importPage(page);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            outputDoc.save(out);
            LOGGER.info("[PptToPdfController#mergePdfs] merged {} pdfs, totalPages={}",
                    pdfs.size(), outputDoc.getNumberOfPages());
            return out.toByteArray();
        } finally {
            outputDoc.close();
        }
    }

    /**
     * 构造单个失败文件的 JSON 片段
     */
    private String jsonError(String filename, String reason) {
        String safeFilename = filename != null ? filename.replace("\"", "\\\"") : "unknown";
        String safeReason = reason != null ? reason.replace("\"", "\\\"") : "转换失败";
        return "{\"filename\":\"" + safeFilename + "\",\"reason\":\"" + safeReason + "\"}";
    }

    /**
     * 获取单页高清预览图
     * <p>
     * 根据 cacheKey 从缓存读取中间 PDF，渲染指定页的高清图片返回。
     * 用于前端点击缩略图放大时按需加载，避免初始预览加载过慢。
     *
     * @param cacheKey 预览阶段返回的缓存 key
     * @param page     页码（从 1 开始）
     * @return JPEG 图片二进制
     */
    @PostMapping("/page-image")
    public ResponseEntity<?> getPageImage(
            @RequestParam("cacheKey") String cacheKey,
            @RequestParam("page") int page) {

        LOGGER.info("[PptToPdfController#getPageImage] cacheKey={}, page={}", cacheKey, page);

        PptPdfCache.CachedEntry cached = pdfCache.get(cacheKey);
        if (cached == null) {
            LOGGER.warn("[PptToPdfController#getPageImage] cache expired, key={}", cacheKey);
            throw new BusinessException(ErrorCodeEnum.PPT_PREVIEW_ERROR);
        }

        try (PDDocument doc = Loader.loadPDF(cached.pdfBytes())) {
            int totalPages = doc.getNumberOfPages();
            if (page < 1 || page > totalPages) {
                throw new BusinessException(ErrorCodeEnum.PDF_PAGE_OUT_OF_RANGE);
            }

            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage image = renderer.renderImageWithDPI(page - 1, PptConvertConstant.HD_DPI);
            byte[] jpegBytes = imageToJpegBytes(image, PptConvertConstant.HD_JPEG_QUALITY);

            LOGGER.info("[PptToPdfController#getPageImage] success, page={}, size={}KB",
                    page, jpegBytes.length / 1024);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(new ByteArrayResource(jpegBytes));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[PptToPdfController#getPageImage] process exception, page={}, error={}",
                    page, e.getMessage(), e);
            throw new BusinessException(ErrorCodeEnum.PPT_PREVIEW_ERROR);
        }
    }

    // ======================== 缓存 ========================

    /**
     * 获取中间 PDF：优先从缓存读取，未命中则重新转换
     */
    private byte[] getCachedOrConvert(MultipartFile file, String filename, String cacheKey) {
        if (!cacheKey.isBlank()) {
            PptPdfCache.CachedEntry cached = pdfCache.get(cacheKey);
            if (cached != null) {
                LOGGER.info("[PptToPdfController#getCachedOrConvert] cache hit, key={}", cacheKey);
                return cached.pdfBytes();
            }
        }

        // 缓存未命中，重新转换
        LOGGER.info("[PptToPdfController#getCachedOrConvert] cache miss, fallback to convert, file={}", filename);
        try {
            return documentService.convertToPdf(file.getBytes(), filename);
        } catch (Exception e) {
            LOGGER.error("[PptToPdfController#getCachedOrConvert] fallback convert exception, file={}, error={}",
                    filename, e.getMessage(), e);
            throw new BusinessException(ErrorCodeEnum.PPT_CONVERT_ERROR);
        }
    }

    // ======================== 页面提取 ========================

    private byte[] extractPages(byte[] pdfBytes, String pageRange) throws IOException {
        try (PDDocument sourceDoc = Loader.loadPDF(pdfBytes)) {
            int totalPages = sourceDoc.getNumberOfPages();
            List<Integer> selectedPages = parsePageRange(pageRange, totalPages);

            PDDocument outputDoc = new PDDocument();
            try {
                for (int pageNum : selectedPages) {
                    PDPage page = sourceDoc.getPage(pageNum - 1);
                    outputDoc.importPage(page);
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                outputDoc.save(out);
                LOGGER.info("[PptToPdfController#extractPages] success, sourcePages={}, selectedPages={}",
                        totalPages, selectedPages.size());
                return out.toByteArray();
            } finally {
                outputDoc.close();
            }
        }
    }

    private List<Integer> parsePageRange(String range, int totalPages) {
        List<Integer> result = new ArrayList<>();
        for (String part : range.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            try {
                int page = Integer.parseInt(part);
                if (page < 1 || page > totalPages) {
                    throw new BusinessException(ErrorCodeEnum.PDF_PAGE_OUT_OF_RANGE);
                }
                if (!result.contains(page)) {
                    result.add(page);
                }
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCodeEnum.PDF_PAGE_FORMAT_ERROR);
            }
        }
        if (result.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PPT_PAGE_SELECTION_EMPTY);
        }
        return result;
    }

    // ======================== 校验 ========================

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PPT_FILE_EMPTY);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !FileTypeValidator.isAllowedPpt(filename)) {
            throw new BusinessException(ErrorCodeEnum.PPT_FORMAT_INVALID);
        }
        if (file.getSize() > PptConvertConstant.MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodeEnum.PPT_FILE_TOO_LARGE);
        }
    }

    // ======================== 工具方法 ========================

    private BufferedImage scaleToMaxWidth(BufferedImage image, int maxWidth) {
        if (image.getWidth() <= maxWidth) return image;
        double ratio = (double) maxWidth / image.getWidth();
        int newHeight = (int) (image.getHeight() * ratio);
        BufferedImage scaled = new BufferedImage(maxWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        scaled.createGraphics().drawImage(
                image.getScaledInstance(maxWidth, newHeight, java.awt.Image.SCALE_SMOOTH),
                0, 0, null);
        return scaled;
    }

    private String imageToBase64(BufferedImage image, float quality) throws IOException {
        return Base64.getEncoder().encodeToString(imageToJpegBytes(image, quality));
    }

    private byte[] imageToJpegBytes(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName(PptConvertConstant.IMAGE_FORMAT_JPEG).next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(ImageIO.createImageOutputStream(bos));
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
