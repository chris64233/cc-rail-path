package com.chris64233.railpath.api;

import java.time.Instant;

/**
 * 不可变径路事件视图。
 */
public record EventView(
        Long id,
        String externalRunNo,
        String eventType,
        Instant occurredAt,
        String detail
) {
}
