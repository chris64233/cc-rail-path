package com.chris64233.railpath.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 径路事件：批准、改线、取消的不可变记录。
 * 只插入，不更新、不删除；detail 保存事件发生时的径路快照。
 */
@Entity
@Table(name = "path_events")
public class PathEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "path_id", nullable = false)
    private TrainPath path;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16)
    private PathEventType type;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** 事件发生时径路内容的快照描述。 */
    @Column(name = "detail", nullable = false, length = 2048)
    private String detail;

    protected PathEvent() {
    }

    public PathEvent(TrainPath path, PathEventType type, LocalDateTime occurredAt, String detail) {
        this.path = path;
        this.type = type;
        this.occurredAt = occurredAt;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public TrainPath getPath() {
        return path;
    }

    public PathEventType getType() {
        return type;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getDetail() {
        return detail;
    }
}
