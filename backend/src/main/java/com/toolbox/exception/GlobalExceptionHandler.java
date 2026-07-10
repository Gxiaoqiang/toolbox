package com.toolbox.exception;

import com.toolbox.model.common.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 全局异常处理器
 *
 * @author toolbox
 * @since 2026-07-01
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusinessException(BusinessException e) {
        LOGGER.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus status = e.getCode() >= 500 ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.BAD_REQUEST;
        R<Void> body = R.fail(e.getCode(), e.getMessage());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 处理缺少必填请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        LOGGER.warn("缺少必填参数: {}", e.getMessage());
        R<Void> body = R.fail(400, "缺少必填参数: " + e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 处理缺少必填请求部分（文件上传）
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<R<Void>> handleMissingPart(MissingServletRequestPartException e) {
        LOGGER.warn("缺少必填请求部分: {}", e.getMessage());
        R<Void> body = R.fail(ErrorCodeEnum.PDF_FILE_EMPTY.getCode(),
                ErrorCodeEnum.PDF_FILE_EMPTY.getDesc());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 处理系统异常
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        LOGGER.error("系统异常", e);
        return R.fail(ErrorCodeEnum.SYSTEM_ERROR.getCode(), ErrorCodeEnum.SYSTEM_ERROR.getDesc());
    }
}
