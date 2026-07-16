package com.toolbox.service.pdf;

/**
 * PDF 转图片常量
 *
 * @author toolbox
 * @since 2026-07-14
 */
public final class ImageConvertConstant {

    private ImageConvertConstant() {}

    /** 最小 DPI */
    public static final int MIN_DPI = 72;

    /** 最大 DPI */
    public static final int MAX_DPI = 600;

    /** 默认 DPI */
    public static final int DEFAULT_DPI = 200;

    /** 默认 JPEG 压缩质量 */
    public static final float DEFAULT_JPEG_QUALITY = 0.9f;

    /** 最大文件大小 */
    public static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    /** 支持导出的图片格式 */
    public enum ImageFormat {
        PNG("png", "image/png"),
        JPEG("jpeg", "image/jpeg");

        private final String extension;
        private final String mimeType;

        ImageFormat(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }

        public String getExtension() { return extension; }

        public String getMimeType() { return mimeType; }

        /** 根据字符串查找格式（大小写不敏感），找不到返回 null */
        public static ImageFormat from(String value) {
            if (value == null) return null;
            String lower = value.toLowerCase();
            for (ImageFormat f : values()) {
                if (f.extension.equals(lower) || f.name().equalsIgnoreCase(lower)) {
                    return f;
                }
            }
            return null;
        }
    }

    /** 单页下载文件名后缀 */
    public static final String SINGLE_PAGE_SUFFIX = "-page-001";
}
