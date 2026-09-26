package com.chris64233.railpath.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 径路申请中的一个区间段。
 *
 * @param sectionCode      区间代码
 * @param entryTime        进入区间时刻（ISO-8601）
 * @param exitTime         离开区间时刻（ISO-8601，左闭右开）
 * @param stopStationCode  离开该区间后需要停靠的车站代码；末段或不停靠时为空
 */
public record LegRequest(
        @NotBlank(message = "区间代码不能为空")
        String sectionCode,

        @NotNull(message = "进入时间不能为空")
        Instant entryTime,

        @NotNull(message = "离开时间不能为空")
        Instant exitTime,

        String stopStationCode
) {
}
