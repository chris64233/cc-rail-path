package com.chris64233.railpath.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 整体改线请求：给出与原申请结构一致的全新径路（区间段序列）。
 * 只有新径路完整取得占用后，旧径路才会释放。
 */
public record RerouteRequest(
        @NotEmpty(message = "新径路至少包含一个区间段")
        @Valid
        List<LegRequest> legs
) {
}
