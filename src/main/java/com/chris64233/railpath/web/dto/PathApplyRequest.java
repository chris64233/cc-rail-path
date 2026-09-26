package com.chris64233.railpath.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/** 径路申请：按顺序给出计划经过的区间与需要停靠的车站。 */
public record PathApplyRequest(
        @NotBlank(message = "外部运行号不能为空") String externalRef,
        @NotEmpty(message = "径路至少包含一个区间") List<@Valid LegRequest> legs,
        List<@Valid StopRequest> stops) {

    public List<StopRequest> stopsOrEmpty() {
        return stops == null ? List.of() : stops;
    }
}
