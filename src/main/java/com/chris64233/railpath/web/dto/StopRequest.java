package com.chris64233.railpath.web.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 径路中一次车站停靠：到达/出发时间。 */
public record StopRequest(
        @NotBlank(message = "车站编号不能为空") String stationCode,
        @NotNull(message = "到达时间不能为空") LocalDateTime arriveTime,
        @NotNull(message = "出发时间不能为空") LocalDateTime departTime) {
}
