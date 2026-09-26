package com.chris64233.railpath.web.dto;

import com.chris64233.railpath.domain.Station;

public record StationResponse(String code, int trackCount) {

    public static StationResponse from(Station station) {
        return new StationResponse(station.getCode(), station.getTrackCount());
    }
}
