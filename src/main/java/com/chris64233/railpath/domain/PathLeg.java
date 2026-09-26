package com.chris64233.railpath.domain;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

/**
 * 径路上的一个占用段：列车在 [entryTime, exitTime) 内占用某个区间或某座车站。
 *
 * <p>时间区间统一采用“左闭右开”：列车离开时刻即释放资源，后续列车可在该时刻进入。
 */
@Entity
@Table(
        name = "path_leg",
        indexes = {
                @Index(name = "idx_path_leg_resource_time",
                        columnList = "leg_type, resource_code, entry_time, exit_time")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_path_leg_reservation_seq",
                        columnNames = {"reservation_id", "seq"})
        },
        check = @CheckConstraint(name = "ck_path_leg_time_window",
                constraint = "exit_time > entry_time")
)
public class PathLeg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private PathReservation reservation;

    /** 在径路中的顺序，从 0 开始，必须连续。 */
    @Column(name = "seq", nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "leg_type", length = 8, nullable = false)
    private LegType legType;

    @Column(name = "resource_code", length = 64, nullable = false)
    private String resourceCode;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "exit_time", nullable = false)
    private Instant exitTime;

    protected PathLeg() {
    }

    public PathLeg(PathReservation reservation, int seq, LegType legType,
                   String resourceCode, Instant entryTime, Instant exitTime) {
        this.reservation = reservation;
        this.seq = seq;
        this.legType = legType;
        this.resourceCode = resourceCode;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
    }

    void bindTo(PathReservation reservation) {
        this.reservation = reservation;
    }

    public Long getId() {
        return id;
    }

    public PathReservation getReservation() {
        return reservation;
    }

    public int getSeq() {
        return seq;
    }

    public LegType getLegType() {
        return legType;
    }

    public String getResourceCode() {
        return resourceCode;
    }

    public Instant getEntryTime() {
        return entryTime;
    }

    public Instant getExitTime() {
        return exitTime;
    }

    /** 时间轴上是否与 [start, end) 重叠（左闭右开）。 */
    public boolean overlaps(Instant start, Instant end) {
        return entryTime.isBefore(end) && start.isBefore(exitTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PathLeg other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
