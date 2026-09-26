package com.chris64233.railpath.domain;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * 铁路区间。
 *
 * <p>记录区间的运行方向、通过能力（同一时刻允许在区间内的最大列车数）以及
 * 最小追踪间隔（同一方向相邻两列车进入区间必须间隔的秒数）。
 */
@Entity
@Table(
        name = "rail_section",
        check = @CheckConstraint(
                name = "ck_rail_section_capacity",
                constraint = "capacity > 0 and min_headway_seconds >= 0")
)
public class RailSection {

    /** 区间代码，业务主键。 */
    @Id
    @Column(name = "code", length = 64, nullable = false, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 8, nullable = false)
    private Direction direction;

    /** 通过能力：同一时刻区间内容许的最大列车数，必须为正。 */
    @Column(name = "capacity", nullable = false)
    private int capacity;

    /** 最小追踪间隔（秒），必须非负。 */
    @Column(name = "min_headway_seconds", nullable = false)
    private long minHeadwaySeconds;

    protected RailSection() {
    }

    public RailSection(String code, Direction direction, int capacity, long minHeadwaySeconds) {
        this.code = code;
        this.direction = direction;
        this.capacity = capacity;
        this.minHeadwaySeconds = minHeadwaySeconds;
    }

    public String getCode() {
        return code;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getCapacity() {
        return capacity;
    }

    public long getMinHeadwaySeconds() {
        return minHeadwaySeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RailSection other)) {
            return false;
        }
        return Objects.equals(code, other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}
