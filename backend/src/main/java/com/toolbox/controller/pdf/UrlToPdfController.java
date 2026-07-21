package com.toolbox.controller.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.pdf.HtmlToPdfService;
import com.toolbox.service.pdf.RenderContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * URL 转 PDF 接口
 *
 * @author toolbox
 * @since 2026-07-19
 */
@RestController
@RequestMapping("/api/pdf")
public class UrlToPdfController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UrlToPdfController.class);

    private final HtmlToPdfService htmlToPdfService;

    public UrlToPdfController(HtmlToPdfService htmlToPdfService) {
        this.htmlToPdfService = htmlToPdfService;
    }

    /**
     * 将 URL 对应的网页转换为 PDF 下载
     *
     * @param params 请求参数（url 必填，其余可选）
     * @return PDF 文件流
     */
    @PostMapping("/url-to-pdf")
    public ResponseEntity<byte[]> urlToPdf(@RequestBody Map<String, Object> params) {
        String url = (String) params.getOrDefault("url", "");
        LOGGER.info("[UrlToPdfController#urlToPdf] url={}", url);

        RenderContext context = buildContext(params);
        byte[] pdfBytes = htmlToPdfService.convertUrl(url, context);

        String filename = "webpage.pdf";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .body(pdfBytes);
    }

    /**
     * 抓取 URL 对应的 HTML 内容，用于前端 srcdoc 预览
     * 解决 iframe 直接加载被 X-Frame-Options 拦截的问题
     *
     * @param url 目标网页 URL
     * @return HTML 内容（text/plain）
     */
    @GetMapping("/preview-html")
    public ResponseEntity<byte[]> previewHtml(@RequestParam String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_EMPTY);
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_INVALID);
        }

        LOGGER.info("[UrlToPdfController#previewHtml] fetching url={}", trimmed);

        try {
            // 使用 Playwright 完整渲染截图——图片/CSS/JS 全部加载，替代 HTTP 抓取 HTML
            byte[] screenshot = htmlToPdfService.previewUrl(trimmed);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(screenshot);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[UrlToPdfController#previewHtml] failed to fetch url={}", trimmed, e);
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_UNREACHABLE);
        }
    }



    /**
     * 从请求参数构建渲染上下文
     *
     * @param params 请求参数 Map
     * @return 渲染上下文
     */
    private RenderContext buildContext(Map<String, Object> params) {
        RenderContext ctx = new RenderContext();

        if (params.containsKey("paperSize")) {
            ctx.setPaperSize((String) params.get("paperSize"));
        }
        if (params.containsKey("orientation")) {
            ctx.setOrientation((String) params.get("orientation"));
        }
        if (params.containsKey("margin")) {
            ctx.setMargin((String) params.get("margin"));
        }
        if (params.containsKey("customMarginMm")) {
            ctx.setCustomMarginMm(toInt(params.get("customMarginMm"), 20));
        }
        if (params.containsKey("scale")) {
            ctx.setScale(toInt(params.get("scale"), 100));
        }
        if (params.containsKey("printBackground")) {
            ctx.setPrintBackground(toBool(params.get("printBackground"), true));
        }
        if (params.containsKey("removeAds")) {
            ctx.setRemoveAds(toBool(params.get("removeAds"), true));
        }
        if (params.containsKey("customHideCss")) {
            ctx.setCustomHideCss((String) params.get("customHideCss"));
        }
        if (params.containsKey("viewport")) {
            ctx.setViewport((String) params.get("viewport"));
        }
        if (params.containsKey("customViewportWidth")) {
            ctx.setCustomViewportWidth(toInt(params.get("customViewportWidth"), 1280));
        }
        if (params.containsKey("headerText")) {
            ctx.setHeaderText((String) params.get("headerText"));
        }
        if (params.containsKey("footerMode")) {
            ctx.setFooterMode((String) params.get("footerMode"));
        }
        if (params.containsKey("footerText")) {
            ctx.setFooterText((String) params.get("footerText"));
        }

        return ctx;
    }

    /** 安全地将 Object 转为 int */
    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    /** 安全地将 Object 转为 boolean */
    private boolean toBool(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }
}
