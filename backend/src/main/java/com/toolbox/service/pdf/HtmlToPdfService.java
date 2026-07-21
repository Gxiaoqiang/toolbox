package com.toolbox.service.pdf;

/**
 * HTML 转 PDF 服务接口
 *
 * @author toolbox
 * @since 2026-07-19
 */
public interface HtmlToPdfService {

    /**
     * 将 URL 对应的网页转换为 PDF
     *
     * @param url     目标网页 URL
     * @param context 渲染上下文（纸张、边距、视口等参数）
     * @return PDF 文件字节数组
     */
    byte[] convertUrl(String url, RenderContext context);

    /**
     * 将本地 HTML 文件内容转换为 PDF
     *
     * @param htmlBytes HTML 文件字节数组
     * @param context   渲染上下文
     * @return PDF 文件字节数组
     */
    byte[] convertHtml(byte[] htmlBytes, RenderContext context);


    /**
     * 使用 Playwright 截图预览 URL——完整渲染含图片/CSS/JS，替代 HTTP 抓取的 getUrlBody()
     *
     * @param url 目标网页 URL
     * @return PNG 格式截图字节数组
     */
    byte[] previewUrl(String url);

    /**
     * 使用 Playwright 截图预览 HTML——完整渲染含图片/CSS/JS
     *
     * @param htmlBytes HTML 文件字节数组
     * @return PNG 格式截图字节数组
     */
    byte[] previewHtml(byte[] htmlBytes);
}
