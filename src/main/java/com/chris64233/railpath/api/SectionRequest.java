package com.chris64233.railpath.api;

import com.chris64233.railpath.domain.Direction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 注册区间请求。
 *
 * @param code              区间代码
 * @param direction         运行方向 UP/DOWN
 * @param capacity          通过能力（同时在区间内的最大列车数）
 * @param minHeadwaySeconds 最小追踪间隔（秒）
 */
public record SectionRequest(
        @NotBlank(message = "区间代码不能为空")
        String code,

        @NotNull(message = "运行方向不能为空")
        Direction direction,

        @Positive(message = "通过能力必须为正")
        int capacity,

        @PositiveOrZero(message = "最小追踪间隔必须为非负数")
        long minHeadwaySeconds
) {
}
