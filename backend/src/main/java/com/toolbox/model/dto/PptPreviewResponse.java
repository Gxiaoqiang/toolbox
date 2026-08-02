package com.toolbox.model.dto;

import java.util.List;

/**
 * PPT 预览响应 DTO
 * <p>
 * 包含 PPT 转换后的 PDF 页面缩略图列表，用于前端页面选择
 *
 * @author toolbox
 * @since 2026-07-22
 */
public class PptPreviewResponse {

    /** 总页数 */
    private int totalPages;

    /** 页面缩略图列表（base64 编码的 JPEG） */
    private List<PageThumbnail> pages;

    /** 原始文件名（不含扩展名） */
    private String baseName;

    /** 缓存 key，用于后续 convert-to-pdf 复用中间 PDF */
    private String cacheKey;

    public PptPreviewResponse() {
    }

    public PptPreviewResponse(int totalPages, List<PageThumbnail> pages, String baseName, String cacheKey) {
        this.totalPages = totalPages;
        this.pages = pages;
        this.baseName = baseName;
        this.cacheKey = cacheKey;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public List<PageThumbnail> getPages() {
        return pages;
    }

    public void setPages(List<PageThumbnail> pages) {
        this.pages = pages;
    }

    public String getBaseName() {
        return baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    /**
     * 单页缩略图
     */
    public static class PageThumbnail {

        /** 页码（从 1 开始） */
        private int pageNumber;

        /** base64 编码的缩略图（JPEG 格式） */
        private String thumbnailBase64;

        /** 缩略图宽度（px） */
        private int width;

        /** 缩略图高度（px） */
        private int height;

        public PageThumbnail() {
        }

        public PageThumbnail(int pageNumber, String thumbnailBase64, int width, int height) {
            this.pageNumber = pageNumber;
            this.thumbnailBase64 = thumbnailBase64;
            this.width = width;
            this.height = height;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public void setPageNumber(int pageNumber) {
            this.pageNumber = pageNumber;
        }

        public String getThumbnailBase64() {
            return thumbnailBase64;
        }

        public void setThumbnailBase64(String thumbnailBase64) {
            this.thumbnailBase64 = thumbnailBase64;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }
    }
}
