package com.toolbox.service.pdf;

/**
 * PDF 压缩常量
 *
 * @author toolbox
 * @since 2026-07-14
 */
public final class PdfCompressConstant {

    private PdfCompressConstant() {}

    /** 压缩等级：极度压缩 — 最低画质、极限体积缩减 */
    public static final int LEVEL_EXTREME = 1;

    /** 压缩等级：高度压缩 — 显著缩体、基础可读性 */
    public static final int LEVEL_HIGH = 2;

    /** 压缩等级：推荐压缩 — 均衡压缩率与视觉质量 */
    public static final int LEVEL_RECOMMENDED = 3;

    /** 压缩等级：轻度压缩 — 较好画质、适度减体 */
    public static final int LEVEL_LIGHT = 4;

    /** 压缩等级：极限画质 — 仅去冗余、近无损 */
    public static final int LEVEL_LOSSLESS = 5;

    /** 默认压缩等级 */
    public static final int DEFAULT_LEVEL = LEVEL_RECOMMENDED;

    /** 最大文件大小 50MB */
    public static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    /**
     * 压缩等级枚举 — 5 档预设，覆盖从极度压缩到极限画质的全场景
     */
    public enum CompressLevel {
        EXTREME(LEVEL_EXTREME,
                "极度压缩",
                "以最低画质换取极限体积缩减。图片降至 72 DPI，JPEG 低质量重编码，移除文档元数据。适合内部流转、长期归档。注意：放大查看时可能出现锯齿和色块",
                72, 0.4f, true),
        HIGH(LEVEL_HIGH,
                "高度压缩",
                "显著缩小体积，保持基础可读性。图片降至 100 DPI，中等 JPEG 质量，移除元数据。适合邮件附件、OA 审批、有文件大小限制的提交",
                100, 0.55f, true),
        RECOMMENDED(LEVEL_RECOMMENDED,
                "推荐压缩",
                "均衡压缩率与视觉质量。图片降至 150 DPI，中高 JPEG 质量，保留文档元数据。适合日常分享、文档归档等大部分通用场景",
                150, 0.7f, false),
        LIGHT(LEVEL_LIGHT,
                "轻度压缩",
                "保持较优质画面，适度减小体积。图片降至 200 DPI，高 JPEG 质量。适合报告、标书、宣传材料等需要较高画质的文档",
                200, 0.85f, false),
        LOSSLESS(LEVEL_LOSSLESS,
                "极限画质",
                "画质优先，仅去除文档冗余数据并轻微压缩图片。图片保持 300 DPI，JPEG 近无损质量。适合画册、设计稿等需放大审阅或再印刷的场景。注意：体积缩减幅度可能较小",
                300, 0.95f, false);

        private final int level;
        private final String label;
        private final String description;
        private final int targetDpi;
        private final float jpegQuality;
        private final boolean removeMeta;

        CompressLevel(int level, String label, String description,
                      int targetDpi, float jpegQuality, boolean removeMeta) {
            this.level = level;
            this.label = label;
            this.description = description;
            this.targetDpi = targetDpi;
            this.jpegQuality = jpegQuality;
            this.removeMeta = removeMeta;
        }

        public int getLevel() { return level; }
        public String getLabel() { return label; }
        public String getDescription() { return description; }
        public int getTargetDpi() { return targetDpi; }
        public float getJpegQuality() { return jpegQuality; }
        public boolean isRemoveMeta() { return removeMeta; }

        /**
         * 根据等级数值查找枚举
         *
         * @param level 压缩等级 1-5
         * @return CompressLevel 枚举值
         * @throws IllegalArgumentException 等级不在 1-5 范围内
         */
        public static CompressLevel fromLevel(int level) {
            for (CompressLevel cl : values()) {
                if (cl.level == level) {
                    return cl;
                }
            }
            throw new IllegalArgumentException("无效的压缩等级: " + level + "，有效范围 1-5");
        }
    }
}
