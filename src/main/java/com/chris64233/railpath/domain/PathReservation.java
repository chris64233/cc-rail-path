package com.chris64233.railpath.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 一条货运列车的完整预留运行径路，按时间顺序由若干区间/车站占用段组成。
 */
@Entity
@Table(name = "path_reservation")
public class PathReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 外部运行号，由申请方提供，全局唯一，用于幂等。 */
    @Column(name = "external_run_no", length = 64, nullable = false, updatable = false, unique = true)
    private String externalRunNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ReservationStatus status;

    /** 申请内容（规范化后）的 SHA-256，用于区分“相同重放”与“同号冲突”。 */
    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("seq ASC")
    private List<PathLeg> legs = new ArrayList<>();

    @Version
    @Column(name = "version")
    private long version;

    protected PathReservation() {
    }

    public PathReservation(String externalRunNo, String requestHash, Instant createdAt) {
        this.externalRunNo = externalRunNo;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
        this.status = ReservationStatus.ACTIVE;
    }

    public void addLeg(int seq, LegType type, String resourceCode, Instant entryTime, Instant exitTime) {
        legs.add(new PathLeg(this, seq, type, resourceCode, entryTime, exitTime));
    }

    /** 清空全部占用段（配合 flush 先执行删除，再写入新段，避免 (径路,序号) 唯一约束冲突）。 */
    public void clearLegs() {
        legs.clear();
    }

    /** 改线成功：写入新占用段并记录新申请内容哈希。 */
    public void applyReroute(String newRequestHash, List<PathLeg> newLegs) {
        legs.addAll(newLegs);
        newLegs.forEach(leg -> leg.bindTo(this));
        this.requestHash = newRequestHash;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public boolean isCancelled() {
        return status == ReservationStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public String getExternalRunNo() {
        return externalRunNo;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<PathLeg> getLegs() {
        return legs;
    }
}
