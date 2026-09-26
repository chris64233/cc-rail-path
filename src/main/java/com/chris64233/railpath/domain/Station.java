package com.chris64233.railpath.domain;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * 车站。记录可用到发线数量，即同一时刻容许在站停靠的最大列车数。
 */
@Entity
@Table(
        name = "station",
        check = @CheckConstraint(name = "ck_station_track_count", constraint = "track_count > 0")
)
public class Station {

    /** 车站代码，业务主键。 */
    @Id
    @Column(name = "code", length = 64, nullable = false, updatable = false)
    private String code;

    /** 可用到发线数量，必须为正。 */
    @Column(name = "track_count", nullable = false)
    private int trackCount;

    protected Station() {
    }

    public Station(String code, int trackCount) {
        this.code = code;
        this.trackCount = trackCount;
    }

    public String getCode() {
        return code;
    }

    public int getTrackCount() {
        return trackCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Station other)) {
            return false;
        }
        return Objects.equals(code, other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}
