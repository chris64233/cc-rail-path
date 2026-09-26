package com.chris64233.railpath.repository;

import com.chris64233.railpath.domain.Station;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, String> {

    Optional<Station> findByCode(String code);

    /** 对车站资源行加悲观写锁，作用与区间锁相同。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Station s where s.code in :codes")
    List<Station> findByCodeInForUpdate(@Param("codes") Collection<String> codes);
}
