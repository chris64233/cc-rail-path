package com.chris64233.railpath.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record StationRequest(
        @NotBlank(message = "车站编号不能为空") String code,
        @Min(value = 1, message = "到发线数量至少为 1") int trackCount) {
}
