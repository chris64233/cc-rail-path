package com.chris64233.railpath.api;

import java.time.Instant;
import java.util.List;

/**
 * 径路查询结果。
 */
public record ReservationView(
        String externalRunNo,
        String status,
        String requestHash,
        Instant createdAt,
        List<OccupancyView> occupancies
) {
}
