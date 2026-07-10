package com.toolbox.util;

import java.util.Set;

/**
 * 文件类型校验工具
 *
 * @author toolbox
 * @since 2026-07-01
 */
public final class FileTypeValidator {

    /** 允许的 Markdown 文件扩展名 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("md", "markdown", "txt");

    /** 允许的文档扩展名（用于 doc-to-pdf） */
    private static final Set<String> ALLOWED_DOC_EXTENSIONS = Set.of("doc", "docx", "wps");

    private FileTypeValidator() {
        // 工具类禁止实例化
    }

    /**
     * 校验文件名是否为允许的类型
     *
     * @param filename 文件名
     * @return 是否合法
     */
    public static boolean isAllowed(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        String ext = filename.substring(dotIndex + 1).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(ext);
    }

    /**
     * 校验文件扩展名是否匹配指定类型
     *
     * @param filename  文件名
     * @param extension 期望的扩展名（不含点号）
     * @return 是否匹配
     */
    public static boolean hasExtension(String filename, String extension) {
        if (filename == null || filename.isEmpty() || extension == null) {
            return false;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        String ext = filename.substring(dotIndex + 1).toLowerCase();
        return ext.equals(extension.toLowerCase());
    }

    /**
     * 校验文件名是否为允许的文档类型
     *
     * @param filename 文件名
     * @return 是否允许
     */
    public static boolean isAllowedDocument(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        String ext = filename.substring(dotIndex + 1).toLowerCase();
        return ALLOWED_DOC_EXTENSIONS.contains(ext);
    }
}
