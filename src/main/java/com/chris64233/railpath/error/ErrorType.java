package com.chris64233.railpath.error;

import org.springframework.http.HttpStatus;

/**
 * 统一业务错误类别。四类错误对应固定的 HTTP 状态码与错误码前缀：
 * 校验、资源、容量、冲突。
 */
public enum ErrorType {
    /** 请求参数或径路内容不合法。 */
    VALIDATION(HttpStatus.BAD_REQUEST, "VALIDATION"),
    /** 引用的区间或车站不存在。 */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE"),
    /** 占用超过区间通过能力或车站到发线数量，或违反追踪间隔。 */
    CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "CAPACITY"),
    /** 同号内容不一致、径路已发车不能改线等业务冲突。 */
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT");

    private final HttpStatus httpStatus;
    private final String codePrefix;

    ErrorType(HttpStatus httpStatus, String codePrefix) {
        this.httpStatus = httpStatus;
        this.codePrefix = codePrefix;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCodePrefix() {
        return codePrefix;
    }
}
