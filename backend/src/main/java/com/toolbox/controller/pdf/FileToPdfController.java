package com.toolbox.controller.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.pdf.HtmlToPdfService;
import com.toolbox.service.pdf.RenderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * HTML 文件转 PDF 接口
 *
 * @author toolbox
 * @since 2026-07-19
 */
@RestController
@RequestMapping("/api/pdf")
public class FileToPdfController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileToPdfController.class);

    /** 允许的 HTML 文件扩展名 */
    private static final String[] ALLOWED_EXTENSIONS = {"html", "htm"};
    /** 文件大小上限 10MB */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final HtmlToPdfService htmlToPdfService;

    public FileToPdfController(HtmlToPdfService htmlToPdfService) {
        this.htmlToPdfService = htmlToPdfService;
    }

    /**
     * 将上传的 HTML 文件转换为 PDF 下载
     *
     * @param file            HTML 文件
     * @param paperSize       纸张大小（可选，默认 A4）
     * @param orientation     页面方向（可选，默认 portrait）
     * @param margin          边距模式（可选，默认 medium）
     * @param customMarginMm  自定义边距（可选）
     * @param scale           缩放比例（可选，默认 100）
     * @param printBackground 是否打印背景（可选，默认 true）
     * @param removeAds       是否去广告（可选，默认 true）
     * @param customHideCss   自定义隐藏 CSS（可选）
     * @param viewport        视口类型（可选，默认 desktop）
     * @param customViewportWidth 自定义视口宽度（可选）
     * @param headerText      页眉文字（可选）
     * @param footerMode      页脚模式（可选，默认 pageNumber）
     * @param footerText      自定义页脚文字（可选）
     * @return PDF 文件流
     */
    @PostMapping("/file-to-pdf")
    public ResponseEntity<byte[]> fileToPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "paperSize", defaultValue = "A4") String paperSize,
            @RequestParam(value = "orientation", defaultValue = "portrait") String orientation,
            @RequestParam(value = "margin", defaultValue = "medium") String margin,
            @RequestParam(value = "customMarginMm", defaultValue = "20") int customMarginMm,
            @RequestParam(value = "scale", defaultValue = "100") int scale,
            @RequestParam(value = "printBackground", defaultValue = "true") boolean printBackground,
            @RequestParam(value = "removeAds", defaultValue = "true") boolean removeAds,
            @RequestParam(value = "customHideCss", defaultValue = "") String customHideCss,
            @RequestParam(value = "viewport", defaultValue = "desktop") String viewport,
            @RequestParam(value = "customViewportWidth", defaultValue = "1280") int customViewportWidth,
            @RequestParam(value = "headerText", defaultValue = "") String headerText,
            @RequestParam(value = "footerMode", defaultValue = "pageNumber") String footerMode,
            @RequestParam(value = "footerText", defaultValue = "") String footerText) {

        // 文件校验
        validateFile(file);

        LOGGER.info("[FileToPdfController#fileToPdf] file={}, size={}, paper={}",
                file.getOriginalFilename(), file.getSize(), paperSize);

        // 构建渲染上下文
        RenderContext context = new RenderContext();
        context.setPaperSize(paperSize);
        context.setOrientation(orientation);
        context.setMargin(margin);
        context.setCustomMarginMm(customMarginMm);
        context.setScale(scale);
        context.setPrintBackground(printBackground);
        context.setRemoveAds(removeAds);
        context.setCustomHideCss(customHideCss);
        context.setViewport(viewport);
        context.setCustomViewportWidth(customViewportWidth);
        context.setHeaderText(headerText);
        context.setFooterMode(footerMode);
        context.setFooterText(footerText);

        try {
            byte[] pdfBytes = htmlToPdfService.convertHtml(file.getBytes(), context);

            String filename = "converted.pdf";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(pdfBytes);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[FileToPdfController#fileToPdf] error processing file", e);
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_RENDER_ERROR);
        }
    }

    /**
     * 校验上传的 HTML 文件
     *
     * @param file 上传的文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_FILE_EMPTY);
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_FORMAT_INVALID);
        }

        String ext = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            ext = filename.substring(dotIndex + 1).toLowerCase();
        }

        boolean validExt = false;
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equals(ext)) {
                validExt = true;
                break;
            }
        }
        if (!validExt) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_FORMAT_INVALID);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_FILE_TOO_LARGE);
        }
    }
}
