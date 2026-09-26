package com.chris64233.railpath.error;

import java.util.List;

/**
 * 业务异常，统一承载错误类别、错误码、提示信息以及具体冲突明细。
 */
public class ApiException extends RuntimeException {

    private final ErrorType type;
    private final String code;
    private final transient List<ConflictDetail> conflicts;

    public ApiException(ErrorType type, String code, String message) {
        this(type, code, message, List.of());
    }

    public ApiException(ErrorType type, String code, String message, List<ConflictDetail> conflicts) {
        super(message);
        this.type = type;
        this.code = code;
        this.conflicts = List.copyOf(conflicts);
    }

    public ErrorType getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    public List<ConflictDetail> getConflicts() {
        return conflicts;
    }
}
