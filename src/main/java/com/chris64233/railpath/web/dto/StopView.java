package com.chris64233.railpath.web.dto;

import java.time.LocalDateTime;

public record StopView(String stationCode, LocalDateTime arriveTime, LocalDateTime departTime) {
}
