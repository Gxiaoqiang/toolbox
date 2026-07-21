package com.toolbox.service.pdf.impl;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitUntilState;
import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.pdf.AdFilterService;
import com.toolbox.service.pdf.HtmlToPdfService;
import com.toolbox.service.pdf.RenderContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * HTML 转 PDF 服务实现
 * 使用 Playwright/Chromium 渲染网页并生成 PDF
 *
 * @author toolbox
 * @since 2026-07-19
 */
@Service
public class HtmlToPdfServiceImpl implements HtmlToPdfService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HtmlToPdfServiceImpl.class);

    /** URL 抓取超时（毫秒） */
    private static final double NAVIGATION_TIMEOUT = 30_000;
    /** 渲染总超时（毫秒） */
    private static final double RENDER_TIMEOUT = 60_000;

    private final AdFilterService adFilterService;

    private Playwright playwright;
    private Browser browser;
    private final Semaphore semaphore = new Semaphore(1);

    /**
     * 构造方法
     *
     * @param adFilterService 广告过滤服务
     */
    public HtmlToPdfServiceImpl(AdFilterService adFilterService) {
        this.adFilterService = adFilterService;
    }

    /**
     * 初始化 Playwright 和 Chromium 浏览器实例
     */
    @PostConstruct
    public void init() {
        LOGGER.info("[HtmlToPdfServiceImpl#init] starting Playwright + Chromium");
        this.playwright = Playwright.create();

        BrowserType.LaunchOptions launchOpts = new BrowserType.LaunchOptions()
                .setHeadless(true);
        // Docker: 使用系统安装的 chromium-browser（环境变量 PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH）
        String chromiumPath = System.getenv("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH");
        if (chromiumPath != null && !chromiumPath.isBlank()) {
            launchOpts.setExecutablePath(Paths.get(chromiumPath));
            LOGGER.info("[HtmlToPdfServiceImpl#init] using system chromium: {}", chromiumPath);
        }

        this.browser = playwright.chromium().launch(launchOpts);
        LOGGER.info("[HtmlToPdfServiceImpl#init] Playwright + Chromium started");
    }

    /**
     * 关闭 Playwright 和 Chromium 浏览器实例
     */
    @PreDestroy
    public void destroy() {
        LOGGER.info("[HtmlToPdfServiceImpl#destroy] shutting down Playwright");
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        LOGGER.info("[HtmlToPdfServiceImpl#destroy] Playwright shut down");
    }

    /**
     * 将 URL 对应的网页转换为 PDF
     *
     * @param url     目标网页 URL
     * @param context 渲染上下文
     * @return PDF 文件字节数组
     */
    @Override
    public byte[] convertUrl(String url, RenderContext context) {
        validateUrl(url);
        LOGGER.info("[HtmlToPdfServiceImpl#convertUrl] url={}, paper={}, orientation={}",
                url, context.getPaperSize(), context.getOrientation());
        return doRender(url, null, context);
    }

    /**
     * 将本地 HTML 内容转换为 PDF
     *
     * @param htmlBytes HTML 文件字节数组
     * @param context   渲染上下文
     * @return PDF 文件字节数组
     */
    @Override
    public byte[] convertHtml(byte[] htmlBytes, RenderContext context) {
        validateHtml(htmlBytes);
        LOGGER.info("[HtmlToPdfServiceImpl#convertHtml] size={}, paper={}, orientation={}",
                htmlBytes.length, context.getPaperSize(), context.getOrientation());
        return doRender(null, htmlBytes, context);
    }


    @Override
    public byte[] previewUrl(String url) {
        validateUrl(url);
        return doScreenshot(url, null);
    }

    @Override
    public byte[] previewHtml(byte[] htmlBytes) {
        validateHtml(htmlBytes);
        return doScreenshot(null, htmlBytes);
    }

    /**
     * Playwright 截图预览 — 完整渲染含图片/CSS/JS，替代 HTTP 抓取
     */
    private byte[] doScreenshot(String url, byte[] htmlBytes) {
        if (!semaphore.tryAcquire()) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_BUSY);
        }
        BrowserContext browserContext = null;
        try {
            browserContext = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(1280, 800));
            browserContext.setDefaultTimeout(NAVIGATION_TIMEOUT);

            Page page = browserContext.newPage();
            if (url != null) {
                page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.NETWORKIDLE)
                        .setTimeout(NAVIGATION_TIMEOUT));
            } else {
                page.setContent(new String(htmlBytes, StandardCharsets.UTF_8),
                        new Page.SetContentOptions()
                                .setWaitUntil(WaitUntilState.NETWORKIDLE)
                                .setTimeout(NAVIGATION_TIMEOUT));
            }

            // 等待图片加载完成
            page.waitForTimeout(3000);

            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(true));

            LOGGER.info("[HtmlToPdfServiceImpl#doScreenshot] success, size={} bytes",
                    screenshot.length);
            return screenshot;

        } catch (Exception e) {
            LOGGER.error("[HtmlToPdfServiceImpl#doScreenshot] failed: {}", e.getMessage());
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_RENDER_ERROR);
        } finally {
            if (browserContext != null) {
                try { browserContext.close(); } catch (Exception ignored) {}
            }
            semaphore.release();
        }
    }

    /**
     * 核心渲染逻辑
     * 创建隔离的 BrowserContext，执行导航/内容设置、广告过滤、PDF 生成
     *
     * @param url       目标 URL（与 htmlBytes 二选一）
     * @param htmlBytes HTML 内容（与 url 二选一）
     * @param context   渲染上下文
     * @return PDF 字节数组
     */
    private byte[] doRender(String url, byte[] htmlBytes, RenderContext context) {
        // 并发控制
        if (!semaphore.tryAcquire()) {
            LOGGER.warn("[HtmlToPdfServiceImpl#doRender] busy, rejecting request");
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_BUSY);
        }

        BrowserContext browserContext = null;
        try {
            // 1. 创建隔离上下文
            int viewportWidth = context.getViewportWidth();
            browserContext = browser.newContext(
                    new Browser.NewContextOptions()
                            .setViewportSize(viewportWidth, 800)
            );
            browserContext.setDefaultTimeout(RENDER_TIMEOUT);

            // 2. 注册广告拦截
            if (context.isRemoveAds()) {
                browserContext.route("**/*", route -> {
                    String requestUrl = route.request().url();
                    if (adFilterService.isAdDomain(requestUrl)) {
                        LOGGER.debug("[AdFilter] blocked: {}", requestUrl);
                        route.abort();
                    } else {
                        route.resume();
                    }
                });
            }

            // 3. 创建页面并导航
            Page page = browserContext.newPage();
            if (url != null) {
                page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.NETWORKIDLE)
                        .setTimeout(NAVIGATION_TIMEOUT));
            } else {
                page.setContent(new String(htmlBytes, StandardCharsets.UTF_8),
                        new Page.SetContentOptions()
                                .setWaitUntil(WaitUntilState.NETWORKIDLE)
                                .setTimeout(NAVIGATION_TIMEOUT));
            }

            // 4. 注入广告隐藏 CSS
            if (context.isRemoveAds()) {
                String hideCss = adFilterService.getHideCss(context.getCustomHideCss());
                page.addStyleTag(new Page.AddStyleTagOptions().setContent(hideCss));
            }

            // 5. 生成 PDF
            Page.PdfOptions pdfOptions = buildPdfOptions(context);
            byte[] pdfBytes = page.pdf(pdfOptions);

            LOGGER.info("[HtmlToPdfServiceImpl#doRender] success, pdf size={} bytes", pdfBytes.length);
            return pdfBytes;

        } catch (PlaywrightException e) {
            LOGGER.error("[HtmlToPdfServiceImpl#doRender] playwright error: {}", e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_RENDER_TIMEOUT);
            }
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_RENDER_ERROR);
        } finally {
            if (browserContext != null) {
                try {
                    browserContext.close();
                } catch (Exception e) {
                    LOGGER.warn("[HtmlToPdfServiceImpl#doRender] error closing context", e);
                }
            }
            semaphore.release();
        }
    }

    /**
     * 构建 Playwright PDF 输出参数
     *
     * @param context 渲染上下文
     * @return Playwright PDF 选项
     */
    private Page.PdfOptions buildPdfOptions(RenderContext context) {
        Page.PdfOptions options = new Page.PdfOptions();

        // 纸张格式
        options.setFormat(context.getPaperSize());

        // 方向
        options.setLandscape("landscape".equals(context.getOrientation()));

        // 边距
        int marginMm = context.getMarginMm();
        String marginStr = marginMm + "mm";
        options.setMargin(new Margin()
                .setTop(marginStr).setRight(marginStr)
                .setBottom(marginStr).setLeft(marginStr));

        // 缩放
        options.setScale(context.getScale() / 100.0);

        // 背景图形
        options.setPrintBackground(context.isPrintBackground());

        // 页眉
        if (context.getHeaderText() != null && !context.getHeaderText().isBlank()) {
            options.setHeaderTemplate("""
                    <div style="font-size:10px; text-align:center; width:100%%;">
                        %s
                    </div>
                    """.formatted(context.getHeaderText()));
        } else {
            options.setHeaderTemplate("<span></span>");
        }

        // 页脚
        String footerMode = context.getFooterMode();
        if (!"none".equals(footerMode)) {
            String footerContent = switch (footerMode) {
                case "pageNumber" ->
                        "<span class='pageNumber'></span> / <span class='totalPages'></span>";
                case "date" ->
                        "<span class='date'></span>";
                case "custom" ->
                        context.getFooterText() != null ? context.getFooterText() : "";
                default -> "";
            };
            options.setFooterTemplate("""
                    <div style="font-size:10px; text-align:center; width:100%%;">
                        %s
                    </div>
                    """.formatted(footerContent));
        } else {
            options.setFooterTemplate("<span></span>");
        }

        return options;
    }

    /**
     * 校验 URL 参数
     *
     * @param url 目标 URL
     */
    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_EMPTY);
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_INVALID);
        }
    }

    /**
     * 校验 HTML 文件内容
     *
     * @param htmlBytes HTML 字节数组
     */
    private void validateHtml(byte[] htmlBytes) {
        if (htmlBytes == null || htmlBytes.length == 0) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_FILE_EMPTY);
        }
        if (htmlBytes.length > 10 * 1024 * 1024) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_FILE_TOO_LARGE);
        }
    }
}
