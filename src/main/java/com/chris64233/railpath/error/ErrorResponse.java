package com.chris64233.railpath.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * 统一错误响应信封。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        List<FieldError> fields,
        List<ConflictDetail> conflicts
) {
    /** 字段级校验错误。 */
    public record FieldError(String field, String message) {
    }
}
