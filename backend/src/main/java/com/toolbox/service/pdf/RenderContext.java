package com.toolbox.service.pdf;

/**
 * HTML 转 PDF 渲染上下文
 * 封装一次转换任务的全部参数
 *
 * @author toolbox
 * @since 2026-07-19
 */
public class RenderContext {

    /** 纸张大小: A4, Letter, Legal */
    private String paperSize = "A4";
    /** 页面方向: portrait, landscape */
    private String orientation = "portrait";
    /** 边距模式: none, narrow, medium, wide, custom */
    private String margin = "medium";
    /** 自定义边距毫米数 (margin=custom 时使用) */
    private int customMarginMm = 20;
    /** 缩放比例 50-200 */
    private int scale = 100;
    /** 是否打印背景色/背景图 */
    private boolean printBackground = true;
    /** 是否开启广告过滤 */
    private boolean removeAds = true;
    /** 用户自定义隐藏 CSS 选择器 */
    private String customHideCss = "";
    /** 视口类型: desktop, tablet, mobile, custom */
    private String viewport = "desktop";
    /** 自定义视口宽度 (viewport=custom 时使用) */
    private int customViewportWidth = 1280;
    /** 页眉文字，空表示无页眉 */
    private String headerText = "";
    /** 页脚模式: none, pageNumber, date, custom */
    private String footerMode = "pageNumber";
    /** 自定义页脚文字 (footerMode=custom 时使用) */
    private String footerText = "";

    // ===== Getters & Setters =====

    public String getPaperSize() { return paperSize; }
    public void setPaperSize(String paperSize) { this.paperSize = paperSize; }

    public String getOrientation() { return orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }

    public String getMargin() { return margin; }
    public void setMargin(String margin) { this.margin = margin; }

    public int getCustomMarginMm() { return customMarginMm; }
    public void setCustomMarginMm(int customMarginMm) { this.customMarginMm = customMarginMm; }

    public int getScale() { return scale; }
    public void setScale(int scale) { this.scale = scale; }

    public boolean isPrintBackground() { return printBackground; }
    public void setPrintBackground(boolean printBackground) { this.printBackground = printBackground; }

    public boolean isRemoveAds() { return removeAds; }
    public void setRemoveAds(boolean removeAds) { this.removeAds = removeAds; }

    public String getCustomHideCss() { return customHideCss; }
    public void setCustomHideCss(String customHideCss) { this.customHideCss = customHideCss; }

    public String getViewport() { return viewport; }
    public void setViewport(String viewport) { this.viewport = viewport; }

    public int getCustomViewportWidth() { return customViewportWidth; }
    public void setCustomViewportWidth(int customViewportWidth) { this.customViewportWidth = customViewportWidth; }

    public String getHeaderText() { return headerText; }
    public void setHeaderText(String headerText) { this.headerText = headerText; }

    public String getFooterMode() { return footerMode; }
    public void setFooterMode(String footerMode) { this.footerMode = footerMode; }

    public String getFooterText() { return footerText; }
    public void setFooterText(String footerText) { this.footerText = footerText; }

    /**
     * 获取当前视口宽度（像素）
     */
    public int getViewportWidth() {
        return switch (viewport) {
            case "tablet" -> 768;
            case "mobile" -> 375;
            case "custom" -> customViewportWidth;
            default -> 1280;
        };
    }

    /**
     * 获取当前边距（毫米）
     */
    public int getMarginMm() {
        return switch (margin) {
            case "none" -> 0;
            case "narrow" -> 10;
            case "wide" -> 30;
            case "custom" -> customMarginMm;
            default -> 20;
        };
    }
}
