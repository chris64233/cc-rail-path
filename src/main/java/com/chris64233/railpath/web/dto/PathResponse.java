package com.chris64233.railpath.web.dto;

import java.util.List;

/** 径路完整占用明细。 */
public record PathResponse(
        String externalRef,
        String status,
        List<LegView> legs,
        List<StopView> stops) {
}
