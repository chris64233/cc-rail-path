package com.chris64233.railpath.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 运行径路申请。legs 按运行先后顺序给出。
 *
 * @param externalRunNo 外部运行号，幂等键
 * @param legs          顺序经过的区间段
 */
public record PathRequest(
        @NotBlank(message = "外部运行号不能为空")
        @Size(max = 64, message = "外部运行号长度不能超过 64")
        String externalRunNo,

        @NotEmpty(message = "径路至少包含一个区间段")
        @Valid
        List<LegRequest> legs
) {
}
