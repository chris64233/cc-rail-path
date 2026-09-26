package com.chris64233.railpath.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.railpath.domain.TrainPath;

import jakarta.persistence.LockModeType;

public interface TrainPathRepository extends JpaRepository<TrainPath, Long> {

    Optional<TrainPath> findByExternalRef(String externalRef);

    /** 改线/取消时锁定径路行，避免并发修改同一径路。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TrainPath p where p.externalRef = :ref")
    Optional<TrainPath> findByExternalRefForUpdate(@Param("ref") String externalRef);
}
