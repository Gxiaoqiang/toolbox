package com.toolbox.service.image;

import java.util.List;

/**
 * 图片转 PDF 服务接口
 *
 * @author toolbox
 * @since 2026-07-19
 */
public interface ImageToPdfService {

    /**
     * 将多张图片合并转换为单个 PDF 文件
     *
     * @param imageBytesList 图片文件字节数组列表
     * @param extensions     每张图片的扩展名（含点号，如 ".jpg"），用于格式识别
     * @param orientation    页面方向：portrait / landscape
     * @param margin         页面边距：none / small / large
     * @param fitMode        图片适配方式：contain / cover / stretch
     * @return 合并后的 PDF 字节数组
     */
    byte[] convertToPdf(List<byte[]> imageBytesList, List<String> extensions,
                        String orientation, String margin, String fitMode);
}
