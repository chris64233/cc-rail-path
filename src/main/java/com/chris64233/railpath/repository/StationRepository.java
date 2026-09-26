package com.chris64233.railpath.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.railpath.domain.Station;

import jakarta.persistence.LockModeType;

public interface StationRepository extends JpaRepository<Station, Long> {

    Optional<Station> findByCode(String code);

    /** 审批/改线时按编号加写锁，序列化对同一车站到发线的并发争抢。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Station s where s.code = :code")
    Optional<Station> findByCodeForUpdate(@Param("code") String code);
}
