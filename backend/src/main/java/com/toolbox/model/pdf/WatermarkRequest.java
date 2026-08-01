package com.toolbox.model.pdf;

/**
 * PDF 添加水印请求 DTO
 * <p>
 * 字段覆盖全部水印能力（来源/外观/位置/页面范围），v1 按工单分批实现。
 * 未配置的字段使用默认值。
 *
 * @author toolbox
 * @since 2026-08-01
 */
public class WatermarkRequest {

    // ===== 水印来源 =====
    /** 来源: "text" | "image" */
    private String source = "text";

    // ===== 文本水印 =====
    /** 水印文本 */
    private String text;
    /** 字体: "heiti"(黑体/默认) | "helvetica" | "times" */
    private String font = "heiti";
    /** 字号（pt） */
    private Float fontSize;
    /** 颜色（hex，如 "#808080"） */
    private String color = "#808080";

    // ===== 外观 =====
    /** 旋转角度（度，-360~360，绕水印自身中心） */
    private Double angle = 0d;
    /** 透明度（0~1） */
    private Double opacity = 0.5d;
    /** 与目标页相对比例（0~100%，图片宽度占页面宽度比例） */
    private Double ratio = 50d;
    /** 固定水印比例（true=跨页大小/位置不变，默认随页面缩放） */
    private Boolean fixedRatio = false;

    // ===== 位置（双轴对齐 + 偏移） =====
    /** 水平对齐: "left" | "center" | "right" */
    private String alignX = "center";
    /** 垂直对齐: "top" | "middle" | "bottom" */
    private String alignY = "middle";
    /** 水平偏移（cm，允许负值） */
    private Double offsetX = 0d;
    /** 垂直偏移（cm，允许负值） */
    private Double offsetY = 0d;

    // ===== 页面范围 =====
    /** 范围: "all" | "pageRange" */
    private String range = "all";
    /** 起始页（1-based，range=pageRange 时生效） */
    private Integer fromPage;
    /** 结束页（1-based，range=pageRange 时生效） */
    private Integer toPage;
    /** 子集: "all" | "odd" | "even" */
    private String subset = "all";

    public WatermarkRequest() {}

    // ===== getter/setter =====

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getFont() { return font; }
    public void setFont(String font) { this.font = font; }

    public Float getFontSize() { return fontSize; }
    public void setFontSize(Float fontSize) { this.fontSize = fontSize; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Double getAngle() { return angle; }
    public void setAngle(Double angle) { this.angle = angle; }

    public Double getOpacity() { return opacity; }
    public void setOpacity(Double opacity) { this.opacity = opacity; }

    public Double getRatio() { return ratio; }
    public void setRatio(Double ratio) { this.ratio = ratio; }

    public Boolean getFixedRatio() { return fixedRatio; }
    public void setFixedRatio(Boolean fixedRatio) { this.fixedRatio = fixedRatio; }

    public String getAlignX() { return alignX; }
    public void setAlignX(String alignX) { this.alignX = alignX; }

    public String getAlignY() { return alignY; }
    public void setAlignY(String alignY) { this.alignY = alignY; }

    public Double getOffsetX() { return offsetX; }
    public void setOffsetX(Double offsetX) { this.offsetX = offsetX; }

    public Double getOffsetY() { return offsetY; }
    public void setOffsetY(Double offsetY) { this.offsetY = offsetY; }

    public String getRange() { return range; }
    public void setRange(String range) { this.range = range; }

    public Integer getFromPage() { return fromPage; }
    public void setFromPage(Integer fromPage) { this.fromPage = fromPage; }

    public Integer getToPage() { return toPage; }
    public void setToPage(Integer toPage) { this.toPage = toPage; }

    public String getSubset() { return subset; }
    public void setSubset(String subset) { this.subset = subset; }
}
