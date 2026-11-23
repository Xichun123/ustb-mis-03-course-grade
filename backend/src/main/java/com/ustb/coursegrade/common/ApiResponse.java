package com.ustb.coursegrade.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 统一响应体，遵循设计报告 3.4 前后端约定：
 * 成功：{code: "OK", message: "...", data: ...}
 * 失败：{code: "ERR_CODE", message: "...", field_errors: {...}, data: null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private String code;
    private String message;
    private Map<String, String> fieldErrors;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(String code, String message, Map<String, String> fieldErrors, T data) {
        this.code = code;
        this.message = message;
        this.fieldErrors = fieldErrors;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "操作成功", null, data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>("OK", message, null, data);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, Map.of(), null);
    }

    public static <T> ApiResponse<T> error(String code, String message, Map<String, String> fieldErrors) {
        return new ApiResponse<>(code, message, fieldErrors == null ? Map.of() : fieldErrors, null);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getField_errors() {
        return fieldErrors;
    }

    public T getData() {
        return data;
    }
}
