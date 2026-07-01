package com.toolbox.service.convert;

/**
 * 文件转换服务接口
 *
 * @author toolbox
 * @since 2026-07-01
 */
public interface ConvertService {

    /**
     * 将 Markdown 文本转换为 DOCX 字节数组
     *
     * @param markdownContent Markdown 文本内容
     * @return DOCX 文件字节数组
     */
    byte[] convertMarkdownToDocx(String markdownContent);
}
