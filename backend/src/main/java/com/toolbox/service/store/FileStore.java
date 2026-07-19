package com.toolbox.service.store;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象 — 可插拔实现：本地磁盘 / OSS / JDBC
 *
 * @author toolbox
 * @since 2026-07-16
 */
public interface FileStore {

    /**
     * 存储上传文件
     *
     * @param file MultipartFile
     * @return fileId 唯一文件标识（含扩展名）
     */
    String store(MultipartFile file);

    /**
     * 存储字节数组（处理结果产物）
     *
     * @param data     字节数据
     * @param filename 文件名（含扩展名）
     * @return fileId
     */
    String store(byte[] data, String filename);

    /**
     * 加载文件内容
     *
     * @param fileId 文件标识（可能不含扩展名，实现需容错模糊匹配）
     * @return 文件字节数组
     */
    byte[] load(String fileId);

    /**
     * 删除指定文件
     *
     * @param fileId 文件标识
     */
    void delete(String fileId);
}
