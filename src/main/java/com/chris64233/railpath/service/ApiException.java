package com.chris64233.railpath.service;

/** 业务异常：携带统一错误码，由全局异常处理器转换为响应。 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
