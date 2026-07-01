package com.toolbox.model.dto;

import java.io.Serializable;

/**
 * 转换结果 DTO
 *
 * @author toolbox
 * @since 2026-07-01
 */
public class ConvertResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 转换后的文本内容 */
    private String content;

    /** 原始文件大小 */
    private Long originalSize;

    /** 转换后文件大小 */
    private Long convertedSize;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getOriginalSize() { return originalSize; }
    public void setOriginalSize(Long originalSize) { this.originalSize = originalSize; }
    public Long getConvertedSize() { return convertedSize; }
    public void setConvertedSize(Long convertedSize) { this.convertedSize = convertedSize; }
}
