package com.chris64233.railpath.web.dto;

import com.chris64233.railpath.domain.Direction;
import com.chris64233.railpath.domain.Section;

public record SectionResponse(String code, Direction direction, int capacity, int minHeadwayMinutes) {

    public static SectionResponse from(Section section) {
        return new SectionResponse(section.getCode(), section.getDirection(),
                section.getCapacity(), section.getMinHeadwayMinutes());
    }
}
