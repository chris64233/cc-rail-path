package com.chris64233.railpath.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.railpath.domain.Section;

import jakarta.persistence.LockModeType;

public interface SectionRepository extends JpaRepository<Section, Long> {

    Optional<Section> findByCode(String code);

    /** 审批/改线时按编号加写锁，序列化对同一区间的并发争抢。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Section s where s.code = :code")
    Optional<Section> findByCodeForUpdate(@Param("code") String code);
}
