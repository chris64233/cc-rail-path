package com.chris64233.railpath.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 径路生命周期的不可变事件（批准 / 改线 / 取消），只追加，不更新、不删除。
 */
@Entity
@Table(
        name = "path_event",
        indexes = {
                @Index(name = "idx_path_event_run_no", columnList = "external_run_no, id")
        }
)
public class PathEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "external_run_no", length = 64, nullable = false, updatable = false)
    private String externalRunNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 16, nullable = false, updatable = false)
    private EventType eventType;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** 事件详情快照（如改线前后的径路 JSON），便于审计。 */
    @Column(name = "detail", length = 4000, updatable = false)
    private String detail;

    protected PathEvent() {
    }

    public PathEvent(String externalRunNo, EventType eventType, Instant occurredAt, String detail) {
        this.externalRunNo = externalRunNo;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public String getExternalRunNo() {
        return externalRunNo;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getDetail() {
        return detail;
    }
}
