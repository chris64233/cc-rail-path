package com.chris64233.railpath.domain;

import org.hibernate.annotations.Check;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 车站：记录可用到发线数量。 */
@Entity
@Table(name = "stations", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Check(constraints = "track_count > 0")
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 车站编号，全局唯一。 */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /** 可用到发线数量：同一时间允许停靠的列车数。 */
    @Column(name = "track_count", nullable = false)
    private int trackCount;

    protected Station() {
    }

    public Station(String code, int trackCount) {
        this.code = code;
        this.trackCount = trackCount;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getTrackCount() {
        return trackCount;
    }
}
