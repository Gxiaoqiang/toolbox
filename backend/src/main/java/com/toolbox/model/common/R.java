package com.toolbox.model.common;

import java.io.Serializable;

/**
 * 统一响应体
 *
 * @param <T> 响应数据类型
 * @author toolbox
 * @since 2026-07-01
 */
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码，0 表示成功 */
    private Integer code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    private R() {}

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(Integer code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.data = null;
        return r;
    }

    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
