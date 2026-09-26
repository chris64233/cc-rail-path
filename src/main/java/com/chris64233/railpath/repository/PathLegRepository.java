package com.chris64233.railpath.repository;

import com.chris64233.railpath.domain.LegType;
import com.chris64233.railpath.domain.PathLeg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PathLegRepository extends JpaRepository<PathLeg, Long> {

    /**
     * 查询某资源上、与时间窗 [windowStart, windowEnd) 重叠（左闭右开）的有效径路占用段，
     * 可排除指定径路自身（改线时旧径路占用不计入冲突）。
     */
    @Query("""
            select distinct l from PathLeg l
            join fetch l.reservation r
            where l.legType = :legType
              and l.resourceCode = :resourceCode
              and l.entryTime < :windowEnd
              and :windowStart < l.exitTime
              and r.status = com.chris64233.railpath.domain.ReservationStatus.ACTIVE
              and (:excludeReservationId is null or r.id <> :excludeReservationId)
            order by l.entryTime asc, l.id asc
            """)
    List<PathLeg> findActiveOverlapping(@Param("legType") LegType legType,
                                        @Param("resourceCode") String resourceCode,
                                        @Param("windowStart") Instant windowStart,
                                        @Param("windowEnd") Instant windowEnd,
                                        @Param("excludeReservationId") Long excludeReservationId);

    /**
     * 查询某资源上进入时刻落在 [fromEntry, toEntry] 范围内的有效占用段。
     * 专供追踪间隔检查：即使占用时间窗不重叠，只要进入时刻过近也算冲突。
     */
    @Query("""
            select distinct l from PathLeg l
            join fetch l.reservation r
            where l.legType = :legType
              and l.resourceCode = :resourceCode
              and l.entryTime >= :fromEntry
              and l.entryTime <= :toEntry
              and r.status = com.chris64233.railpath.domain.ReservationStatus.ACTIVE
              and (:excludeReservationId is null or r.id <> :excludeReservationId)
            order by l.entryTime asc, l.id asc
            """)
    List<PathLeg> findActiveByEntryTimeBetween(@Param("legType") LegType legType,
                                               @Param("resourceCode") String resourceCode,
                                               @Param("fromEntry") Instant fromEntry,
                                               @Param("toEntry") Instant toEntry,
                                               @Param("excludeReservationId") Long excludeReservationId);
}
