package com.toolbox.service.ppt;

/**
 * PPT 转 PDF 常量
 *
 * @author toolbox
 * @since 2026-07-22
 */
public final class PptConvertConstant {

    private PptConvertConstant() {}

    // ======================== 预览缩略图 ========================

    /** 缩略图渲染 DPI（低分辨率加速加载） */
    public static final int THUMBNAIL_DPI = 72;

    /** 缩略图最大宽度（px） */
    public static final int THUMBNAIL_MAX_WIDTH = 200;

    /** 缩略图 JPEG 压缩质量 */
    public static final float THUMBNAIL_QUALITY = 0.7f;

    // ======================== 高清预览图 ========================

    /** 高清图渲染 DPI（点击放大时按需加载） */
    public static final int HD_DPI = 150;

    /** 高清图 JPEG 压缩质量 */
    public static final float HD_JPEG_QUALITY = 0.85f;

    // ======================== 文件限制 ========================

    /** 最大文件大小（50MB） */
    public static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    // ======================== 图片格式 ========================

    /** JPEG 格式标识 */
    public static final String IMAGE_FORMAT_JPEG = "jpeg";

    /** JPEG MIME 类型 */
    public static final String MIME_TYPE_JPEG = "image/jpeg";

    // ======================== 缓存 ========================

    /** 缓存过期时间（ms）：10 分钟 */
    public static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    /** 缓存定时清理间隔（ms）：5 分钟 */
    public static final long CACHE_CLEANUP_INTERVAL_MS = 300_000L;

    /** Redis 缓存 key 前缀 */
    public static final String REDIS_KEY_PREFIX = "toolbox:ppt-cache:";
}
