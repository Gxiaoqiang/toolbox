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
    CONVERT_ERROR(500, "文件转换失败");

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
