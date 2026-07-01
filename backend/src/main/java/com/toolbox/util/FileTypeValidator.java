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
}
