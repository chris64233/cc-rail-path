package com.chris64233.railpath.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/** 改线申请：整体替换为新径路。 */
public record RerouteRequest(
        @NotEmpty(message = "径路至少包含一个区间") List<@Valid LegRequest> legs,
        List<@Valid StopRequest> stops) {

    public List<StopRequest> stopsOrEmpty() {
        return stops == null ? List.of() : stops;
    }
}
