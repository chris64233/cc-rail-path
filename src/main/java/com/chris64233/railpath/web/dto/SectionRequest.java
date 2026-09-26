package com.chris64233.railpath.web.dto;

import com.chris64233.railpath.domain.Direction;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SectionRequest(
        @NotBlank(message = "区间编号不能为空") String code,
        @NotNull(message = "方向不能为空") Direction direction,
        @Min(value = 1, message = "通过能力至少为 1") int capacity,
        @Min(value = 0, message = "最小追踪间隔不能为负") int minHeadwayMinutes) {
}
