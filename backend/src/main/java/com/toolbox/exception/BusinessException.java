package com.toolbox.exception;

/**
 * 业务异常
 *
 * @author toolbox
 * @since 2026-07-01
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final Integer code;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCodeEnum errorCode) {
        super(errorCode.getDesc());
        this.code = errorCode.getCode();
    }

    /**
     * 使用错误码 + 附加消息构造
     *
     * @param errorCode 错误码枚举
     * @param extra     附加到默认描述之后的消息
     */
    public BusinessException(ErrorCodeEnum errorCode, String extra) {
        super(errorCode.getDesc() + extra);
        this.code = errorCode.getCode();
    }

    public Integer getCode() { return code; }
}
