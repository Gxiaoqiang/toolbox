package com.toolbox.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    /** 允许的 PPT 扩展名（用于 ppt-to-pdf） */
    private static final Set<String> ALLOWED_PPT_EXTENSIONS = Set.of("ppt", "pptx");

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
     * 校验 PDF 文件魔数（文件头 4 字节应为 %PDF）
     * <p>
     * 防止攻击者将恶意文件改扩展名上传。
     *
     * @param header 文件前几个字节（至少 4 字节）
     * @return true=合法的 PDF 文件头
     */
    public static boolean isValidPdfMagic(byte[] header) {
        if (header == null || header.length < 4) {
            return false;
        }
        // PDF 文件必须以 %PDF 开头（ASCII: 0x25 0x50 0x44 0x46）
        return header[0] == 0x25 && header[1] == 0x50
                && header[2] == 0x44 && header[3] == 0x46;
    }

    /**
     * 校验图片文件魔数
     * <p>
     * 检测常见图片格式（JPG / PNG / WEBP / GIF）的真实文件头。
     *
     * @param header 文件前几个字节（至少 12 字节）
     * @return true=合法的图片文件头
     */
    public static boolean isValidImageMagic(byte[] header) {
        if (header == null || header.length < 4) {
            return false;
        }

        // JPEG: FF D8 FF
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return true;
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (header.length >= 8
                && header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E
                && header[3] == 0x47) {
            return true;
        }

        // GIF: GIF87a 或 GIF89a
        if (header.length >= 6
                && (header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                && header[3] == '8' && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a')) {
            return true;
        }

        // WEBP: RIFF....WEBP
        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return true;
        }

        return false;
    }

    /**
     * 读取 MultipartFile 的文件头字节（用于魔数校验）
     *
     * @param file 上传文件
     * @param len  需要读取的字节数
     * @return 文件头字节数组（读取失败返回空数组）
     */
    public static byte[] readHeader(MultipartFile file, int len) {
        try {
            byte[] data = new byte[len];
            int read = file.getInputStream().read(data, 0, len);
            if (read < len) {
                return new byte[0];
            }
            return data;
        } catch (IOException e) {
            return new byte[0];
        }
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

    /**
     * 校验文件是否为允许的 PPT 格式（.ppt / .pptx）
     *
     * @param filename 文件名
     * @return 是否允许
     */
    public static boolean isAllowedPpt(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        String ext = filename.substring(dotIndex + 1).toLowerCase();
        return ALLOWED_PPT_EXTENSIONS.contains(ext);
    }
}
