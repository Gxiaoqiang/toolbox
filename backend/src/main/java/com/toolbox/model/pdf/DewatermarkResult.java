package com.toolbox.model.pdf;

import java.util.List;

/**
 * PDF 去水印结果 DTO（区域级反馈）
 *
 * @author toolbox
 * @since 2026-08-01
 */
public class DewatermarkResult {

    /** 去水印后 PDF 的 base64 编码 */
    private String pdfBase64;

    /** 成功去除水印的区域列表 */
    private List<RegionResult> removed;

    /** 无法自动去除的区域列表 */
    private List<RegionResult> failed;

    public DewatermarkResult() {}

    public String getPdfBase64() { return pdfBase64; }
    public void setPdfBase64(String pdfBase64) { this.pdfBase64 = pdfBase64; }

    public List<RegionResult> getRemoved() { return removed; }
    public void setRemoved(List<RegionResult> removed) { this.removed = removed; }

    public List<RegionResult> getFailed() { return failed; }
    public void setFailed(List<RegionResult> failed) { this.failed = failed; }

    /**
     * 单个区域的处理结果（坐标回显前端传入的原值，便于画布定位）
     */
    public static class RegionResult {

        /** 页码（0-based） */
        private int page;

        /** 区域 X（前端左上角原点坐标） */
        private double x;

        /** 区域 Y（前端左上角原点坐标） */
        private double y;

        /** 区域宽度（points） */
        private double w;

        /** 区域高度（points） */
        private double h;

        public RegionResult() {}

        public RegionResult(int page, double x, double y, double w, double h) {
            this.page = page;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }

        public double getY() { return y; }
        public void setY(double y) { this.y = y; }

        public double getW() { return w; }
        public void setW(double w) { this.w = w; }

        public double getH() { return h; }
        public void setH(double h) { this.h = h; }
    }
}
