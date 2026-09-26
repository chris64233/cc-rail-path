package com.chris64233.railpath.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.Check;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 区间占用：径路在某一区间上的进入/离开时间。 */
@Entity
@Table(name = "path_legs")
@Check(constraints = "enter_time < exit_time")
public class PathLeg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "path_id", nullable = false)
    private TrainPath path;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    /** 在径路中的顺序号，从 0 开始。 */
    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Column(name = "enter_time", nullable = false)
    private LocalDateTime enterTime;

    @Column(name = "exit_time", nullable = false)
    private LocalDateTime exitTime;

    protected PathLeg() {
    }

    public PathLeg(TrainPath path, Section section, int seqNo, LocalDateTime enterTime, LocalDateTime exitTime) {
        this.path = path;
        this.section = section;
        this.seqNo = seqNo;
        this.enterTime = enterTime;
        this.exitTime = exitTime;
    }

    public Long getId() {
        return id;
    }

    public TrainPath getPath() {
        return path;
    }

    public Section getSection() {
        return section;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public LocalDateTime getEnterTime() {
        return enterTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }
}
