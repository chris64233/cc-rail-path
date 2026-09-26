package com.chris64233.railpath.error;

import java.util.List;

/**
 * 单条冲突明细：冲突发生的资源、时间窗以及与之冲突的运行号。
 */
public record ConflictDetail(
        String resourceType,
        String resourceCode,
        String reason,
        String windowStart,
        String windowEnd,
        List<String> conflictingRunNos
) {
}
