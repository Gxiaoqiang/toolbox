package com.toolbox.model.pdf;

import java.util.List;

/**
 * PDF 涂黑遮盖请求 DTO
 *
 * @author toolbox
 * @since 2026-07-30
 */
public class RedactRequest {

    /** 遮盖模式: standard / deep */
    private String mode;

    /** 所有页面的方块列表 */
    private List<RectItem> rects;

    public RedactRequest() {}

    public RedactRequest(String mode, List<RectItem> rects) {
        this.mode = mode;
        this.rects = rects;
    }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public List<RectItem> getRects() { return rects; }
    public void setRects(List<RectItem> rects) { this.rects = rects; }

    /**
     * 单个遮盖方块
     */
    public static class RectItem {

        /** 页码（0-based） */
        private int page;

        /** PDF 坐标 X（points，原点左下角） */
        private double x;

        /** PDF 坐标 Y（points） */
        private double y;

        /** 宽度（points） */
        private double w;

        /** 高度（points） */
        private double h;

        /** 颜色（hex 格式 #RRGGBB） */
        private String color;

        public RectItem() {}

        public RectItem(int page, double x, double y, double w, double h, String color) {
            this.page = page;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.color = color;
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

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }

        /**
         * 校验方块数据是否有效
         */
        public boolean isValid() {
            return page >= 0 && w > 0 && h > 0 && color != null && !color.isEmpty();
        }
    }
}
