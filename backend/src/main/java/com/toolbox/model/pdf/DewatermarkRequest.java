package com.toolbox.model.pdf;

import java.util.List;

/**
 * PDF 去水印请求 DTO
 *
 * @author toolbox
 * @since 2026-08-01
 */
public class DewatermarkRequest {

    /** 应用范围: all(所有页) / page(仅指定页) */
    private String applyTo;

    /** 水印区域列表 */
    private List<RegionItem> regions;

    public DewatermarkRequest() {}

    public DewatermarkRequest(String applyTo, List<RegionItem> regions) {
        this.applyTo = applyTo;
        this.regions = regions;
    }

    public String getApplyTo() { return applyTo; }
    public void setApplyTo(String applyTo) { this.applyTo = applyTo; }

    public List<RegionItem> getRegions() { return regions; }
    public void setRegions(List<RegionItem> regions) { this.regions = regions; }

    /**
     * 单个水印区域
     */
    public static class RegionItem {

        /** 页码（0-based），applyTo=page 时生效，all 时忽略 */
        private int page;

        /** 区域 X（PDF 坐标 points，前端为左上角原点，服务端翻转） */
        private double x;

        /** 区域 Y（PDF 坐标 points） */
        private double y;

        /** 区域宽度（points） */
        private double w;

        /** 区域高度（points） */
        private double h;

        public RegionItem() {}

        public RegionItem(int page, double x, double y, double w, double h) {
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

        /**
         * 校验区域数据是否有效
         */
        public boolean isValid() {
            return page >= 0 && w > 0 && h > 0;
        }
    }
}
