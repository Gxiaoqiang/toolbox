package com.toolbox.service.pdf;

import java.util.Map;

/**
 * HTML 转 PDF 服务接口
 *
 * @author toolbox
 * @since 2026-07-19
 */
public interface HtmlToPdfService {

    /**
     * 将 URL 对应的网页转换为 PDF
     */
    byte[] convertUrl(String url, RenderContext context);

    /**
     * 将本地 HTML 文件内容转换为 PDF
     */
    byte[] convertHtml(byte[] htmlBytes, RenderContext context);

    /**
     * 将 HTML 及其关联资源（CSS/JS/图片）转换为 PDF
     * 通过临时目录 + file:// 协议导航，Playwright 正确解析相对路径
     */
    byte[] convertHtmlWithAssets(byte[] htmlBytes, Map<String, byte[]> assets, RenderContext context);

    /**
     * 使用 Playwright 截图预览 URL
     */
    byte[] previewUrl(String url);

    /**
     * 使用 Playwright 截图预览 HTML
     */
    byte[] previewHtml(byte[] htmlBytes);

    /**
     * 使用 Playwright 截图预览 HTML 及其关联资源
     */
    byte[] previewHtmlWithAssets(byte[] htmlBytes, Map<String, byte[]> assets);
}
