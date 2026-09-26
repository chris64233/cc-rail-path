package com.chris64233.railpath.web.dto;

import java.util.List;

/** 统一错误响应体。 */
public record ErrorResponse(String code, String message, List<String> details) {

    public ErrorResponse(String code, String message) {
        this(code, message, List.of());
    }
}
