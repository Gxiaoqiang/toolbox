package com.toolbox.exception;

/**
 * 统一错误码枚举
 *
 * @author toolbox
 * @since 2026-07-01
 */
public enum ErrorCodeEnum {

    /** 系统未知异常 */
    SYSTEM_ERROR(500, "系统内部错误"),
    /** 参数校验失败 */
    PARAM_INVALID(400, "参数校验失败"),
    /** 文件格式不支持 */
    FILE_FORMAT_UNSUPPORTED(400, "文件格式不支持"),
    /** 文件转换失败 */
    CONVERT_ERROR(500, "文件转换失败"),
    /** PDF 文件格式不正确 */
    PDF_FORMAT_INVALID(400, "请上传 PDF 格式的文件"),
    /** PDF 文件为空 */
    PDF_FILE_EMPTY(400, "请选择有效的 PDF 文件"),
    /** PDF 已加密 */
    PDF_ENCRYPTED(400, "暂不支持加密的 PDF 文件"),
    /** 页码超出范围 */
    PDF_PAGE_OUT_OF_RANGE(400, "页码范围超出文档总页数"),
    /** 页码格式错误 */
    PDF_PAGE_FORMAT_ERROR(400, "页码范围格式不正确，请输入如 \"1,3,5-8\""),
    /** 页码重复或重叠 */
    PDF_PAGE_OVERLAP(400, "页码范围存在重复或重叠"),
    /** 每 N 页参数无效 */
    PDF_EVERY_N_INVALID(400, "每页拆分数量必须为正整数"),
    /** PDF 处理异常 */
    PDF_PROCESS_ERROR(500, "PDF 处理失败，请稍后重试"),
    /** 文档格式不支持 */
    DOC_FORMAT_INVALID(400, "仅支持 .doc / .docx / .wps 格式"),
    /** 文档文件为空 */
    DOC_FILE_EMPTY(400, "请选择有效的文档文件"),
    /** 超过最大文件数 */
    DOC_TOO_MANY_FILES(400, "单次最多上传 5 个文件"),
    /** 文档文件超过大小限制 */
    DOC_FILE_TOO_LARGE(400, "单个文件不能超过 50MB"),
    /** 文档转换失败 */
    DOC_CONVERT_ERROR(500, "文档转换失败"),
    /** 转换服务不可用 */
    DOC_SERVICE_UNAVAILABLE(500, "转换服务不可用，请联系管理员");

    /** 错误码 */
    private final Integer code;
    /** 错误描述 */
    private final String desc;

    ErrorCodeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() { return code; }
    public String getDesc() { return desc; }
}
