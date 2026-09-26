package com.chris64233.railpath.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.railpath.domain.PathStop;

public interface PathStopRepository extends JpaRepository<PathStop, Long> {

    @Query("select s from PathStop s join fetch s.station join fetch s.path where s.station.id in :stationIds")
    List<PathStop> findByStationIdIn(@Param("stationIds") Collection<Long> stationIds);
}
