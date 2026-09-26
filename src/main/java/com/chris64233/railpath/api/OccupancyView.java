package com.chris64233.railpath.api;

import java.time.Instant;

/**
 * 占用明细中的一段。
 *
 * @param seq          径路内顺序
 * @param resourceType SECTION 或 STATION
 * @param resourceCode 区间/车站代码
 * @param entryTime    占用开始（进入）时刻
 * @param exitTime     占用结束（离开）时刻，左闭右开
 */
public record OccupancyView(
        int seq,
        String resourceType,
        String resourceCode,
        Instant entryTime,
        Instant exitTime
) {
}
