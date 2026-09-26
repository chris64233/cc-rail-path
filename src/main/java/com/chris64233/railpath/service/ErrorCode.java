package com.chris64233.railpath.service;

import org.springframework.http.HttpStatus;

/** 统一错误码：校验、资源、容量、冲突。 */
public enum ErrorCode {
    /** 请求内容或时间线校验失败 */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    /** 引用的区间/车站/运行号不存在 */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** 区间通过能力或车站到发线不足 */
    CAPACITY_EXCEEDED(HttpStatus.CONFLICT),
    /** 不满足区间最小追踪间隔 */
    HEADWAY_VIOLATION(HttpStatus.CONFLICT),
    /** 幂等冲突或并发写入冲突 */
    CONFLICT(HttpStatus.CONFLICT),
    /** 当前状态不允许该操作（如已发车改线、重复取消） */
    INVALID_STATE(HttpStatus.CONFLICT);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
