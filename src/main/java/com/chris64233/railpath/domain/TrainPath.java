package com.chris64233.railpath.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 列车运行径路：一次批准即整体生效的区间占用与车站停靠集合。 */
@Entity
@Table(name = "train_paths", uniqueConstraints = @UniqueConstraint(columnNames = "external_ref"))
public class TrainPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部运行号，幂等键，全局唯一。 */
    @Column(name = "external_ref", nullable = false, length = 64)
    private String externalRef;

    /** 申请内容摘要，用于幂等重放的一致性比对。 */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PathStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "path", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seqNo ASC")
    private List<PathLeg> legs = new ArrayList<>();

    @OneToMany(mappedBy = "path", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seqNo ASC")
    private List<PathStop> stops = new ArrayList<>();

    @OneToMany(mappedBy = "path", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<PathEvent> events = new ArrayList<>();

    protected TrainPath() {
    }

    public TrainPath(String externalRef, String contentHash, LocalDateTime createdAt) {
        this.externalRef = externalRef;
        this.contentHash = contentHash;
        this.status = PathStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    public void addLeg(PathLeg leg) {
        legs.add(leg);
    }

    public void addStop(PathStop stop) {
        stops.add(stop);
    }

    public void addEvent(PathEvent event) {
        events.add(event);
    }

    /** 整体改线/取消时清空全部占用（orphanRemoval 负责删除旧行）。 */
    public void clearOccupancy() {
        legs.clear();
        stops.clear();
    }

    public void cancel() {
        clearOccupancy();
        this.status = PathStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public PathStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<PathLeg> getLegs() {
        return legs;
    }

    public List<PathStop> getStops() {
        return stops;
    }

    public List<PathEvent> getEvents() {
        return events;
    }
}
