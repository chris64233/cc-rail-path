package com.chris64233.railpath.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 注册车站请求。
 *
 * @param code       车站代码
 * @param trackCount 可用到发线数量
 */
public record StationRequest(
        @NotBlank(message = "车站代码不能为空")
        String code,

        @Positive(message = "到发线数量必须为正")
        int trackCount
) {
}
