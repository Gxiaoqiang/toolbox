package com.toolbox.service.pdf;

/**
 * PDF 加密服务接口
 *
 * @author toolbox
 * @since 2026-07-19
 */
public interface PdfEncryptService {

    /**
     * 对 PDF 文件进行加密
     *
     * @param pdfBytes      PDF 文件字节数组
     * @param userPassword  用户密码（打开密码），可为空
     * @param ownerPassword 所有者密码（权限密码），可为空
     * @param canPrint      允许打印
     * @param canCopy       允许复制/提取内容
     * @param canModify     允许修改文档内容
     * @param canAnnotate   允许编辑注释和填写表单
     * @param canAssemble   允许页面组装
     * @return 加密后的 PDF 字节数组
     */
    byte[] encrypt(byte[] pdfBytes, String userPassword, String ownerPassword,
                   boolean canPrint, boolean canCopy, boolean canModify,
                   boolean canAnnotate, boolean canAssemble);
}
