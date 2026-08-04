package com.toolbox.exception;

import com.toolbox.model.common.R;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
     *
     * @param e       业务异常
     * @param request 当前请求（用于打印请求上下文，便于日志排查）
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusinessException(BusinessException e,
                                                            HttpServletRequest request) {
        LOGGER.warn("业务异常: code={}, message={}, {}",
                e.getCode(), e.getMessage(), requestContext(request), e);
        HttpStatus status = e.getCode() >= 500 ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.BAD_REQUEST;
        R<Void> body = R.fail(e.getCode(), e.getMessage());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 处理缺少必填请求参数
     *
     * @param e       异常
     * @param request 当前请求（用于打印请求上下文，便于日志排查）
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParam(MissingServletRequestParameterException e,
                                                       HttpServletRequest request) {
        LOGGER.warn("缺少必填参数: {}, {}", e.getMessage(), requestContext(request), e);
        R<Void> body = R.fail(400, "缺少必填参数: " + e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 处理缺少必填请求部分（文件上传）
     *
     * @param e       异常
     * @param request 当前请求（用于打印请求上下文，便于日志排查）
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<R<Void>> handleMissingPart(MissingServletRequestPartException e,
                                                      HttpServletRequest request) {
        LOGGER.warn("缺少必填请求部分: {}, {}", e.getMessage(), requestContext(request), e);
        R<Void> body = R.fail(ErrorCodeEnum.PDF_FILE_EMPTY.getCode(),
                ErrorCodeEnum.PDF_FILE_EMPTY.getDesc());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 处理静态资源未找到（预览 iframe 请求相对路径资源导致）
     *
     * @param e       异常
     * @param request 当前请求（用于打印请求上下文，便于日志排查）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e,
                                                       HttpServletRequest request) {
        LOGGER.debug("[GlobalExceptionHandler#handleNoResourceFound] {}, {}",
                e.getResourcePath(), requestContext(request));
        return ResponseEntity.notFound().build();
    }

    /**
     * 处理系统异常
     *
     * @param e       系统异常
     * @param request 当前请求（用于打印请求上下文，便于日志排查）
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        LOGGER.error("系统异常: {}", requestContext(request), e);
        return R.fail(ErrorCodeEnum.SYSTEM_ERROR.getCode(), ErrorCodeEnum.SYSTEM_ERROR.getDesc());
    }

    /**
     * 拼接请求上下文信息，用于日志排查定位请求来源
     *
     * @param request 当前请求
     * @return 形如 method=[POST] uri=[/api/ppt/preview] query=[page=1&type=2] 的字符串
     */
    private String requestContext(HttpServletRequest request) {
        if (request == null) {
            return "request=null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("method=[").append(request.getMethod()).append(']');
        sb.append(" uri=[").append(request.getRequestURI()).append(']');
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            sb.append(" query=[").append(request.getQueryString()).append(']');
        }
        return sb.toString();
    }
}
