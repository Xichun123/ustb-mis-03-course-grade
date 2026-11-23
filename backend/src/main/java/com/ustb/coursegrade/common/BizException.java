package com.ustb.coursegrade.common;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 业务异常：按设计报告 3.4 映射标准 HTTP 状态码（401/403/404/409/422）
 */
public class BizException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public BizException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public BizException(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Map.of() : fieldErrors;
    }

    public static BizException unauthorized(String message) {
        return new BizException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static BizException forbidden(String message) {
        return new BizException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static BizException notFound(String message) {
        return new BizException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static BizException conflict(String code, String message) {
        return new BizException(HttpStatus.CONFLICT, code, message);
    }

    public static BizException unprocessable(String code, String message) {
        return new BizException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static BizException unprocessable(String code, String message, Map<String, String> fieldErrors) {
        return new BizException(HttpStatus.UNPROCESSABLE_ENTITY, code, message, fieldErrors);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
