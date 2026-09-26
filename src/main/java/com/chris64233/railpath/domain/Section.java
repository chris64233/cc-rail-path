package com.chris64233.railpath.domain;

import org.hibernate.annotations.Check;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 铁路区间：记录方向、通过能力和最小追踪间隔。 */
@Entity
@Table(name = "sections", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Check(constraints = "capacity > 0 AND min_headway_minutes >= 0")
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 区间编号，全局唯一。 */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 8)
    private Direction direction;

    /** 通过能力：同一时间允许占用本区间的列车数。 */
    @Column(name = "capacity", nullable = false)
    private int capacity;

    /** 最小追踪间隔（分钟）：同区间任意两列车进入/离开时间差不得小于该值。 */
    @Column(name = "min_headway_minutes", nullable = false)
    private int minHeadwayMinutes;

    protected Section() {
    }

    public Section(String code, Direction direction, int capacity, int minHeadwayMinutes) {
        this.code = code;
        this.direction = direction;
        this.capacity = capacity;
        this.minHeadwayMinutes = minHeadwayMinutes;
    }

    public Long getId() {
        return id;
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

    public int getMinHeadwayMinutes() {
        return minHeadwayMinutes;
    }
}
