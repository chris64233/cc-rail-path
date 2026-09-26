package com.chris64233.railpath.web.dto;

import java.time.LocalDateTime;

public record EventView(String type, LocalDateTime occurredAt, String detail) {
}
