package com.chris64233.railpath.web.dto;

import java.time.LocalDateTime;

public record LegView(String sectionCode, LocalDateTime enterTime, LocalDateTime exitTime) {
}
