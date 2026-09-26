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

/** 车站停靠：径路在某一车站占用到发线的时间窗。 */
@Entity
@Table(name = "path_stops")
@Check(constraints = "arrive_time < depart_time")
public class PathStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "path_id", nullable = false)
    private TrainPath path;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    /** 在径路中的顺序号，从 0 开始。 */
    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Column(name = "arrive_time", nullable = false)
    private LocalDateTime arriveTime;

    @Column(name = "depart_time", nullable = false)
    private LocalDateTime departTime;

    protected PathStop() {
    }

    public PathStop(TrainPath path, Station station, int seqNo, LocalDateTime arriveTime, LocalDateTime departTime) {
        this.path = path;
        this.station = station;
        this.seqNo = seqNo;
        this.arriveTime = arriveTime;
        this.departTime = departTime;
    }

    public Long getId() {
        return id;
    }

    public TrainPath getPath() {
        return path;
    }

    public Station getStation() {
        return station;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public LocalDateTime getArriveTime() {
        return arriveTime;
    }

    public LocalDateTime getDepartTime() {
        return departTime;
    }
}
