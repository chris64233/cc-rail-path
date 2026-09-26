package com.chris64233.railpath.web.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 径路中一个区间段：进入/离开时间。 */
public record LegRequest(
        @NotBlank(message = "区间编号不能为空") String sectionCode,
        @NotNull(message = "进入时间不能为空") LocalDateTime enterTime,
        @NotNull(message = "离开时间不能为空") LocalDateTime exitTime) {
}
