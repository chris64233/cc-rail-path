package com.chris64233.railpath.repository;

import com.chris64233.railpath.domain.RailSection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RailSectionRepository extends JpaRepository<RailSection, String> {

    Optional<RailSection> findByCode(String code);

    /**
     * 对区间资源行加悲观写锁。并发申请争抢同一区间时在此排队，
     * 后到的事务必须等先到事务提交后才能读到最新占用，保证容量判定串行化。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RailSection s where s.code in :codes")
    List<RailSection> findByCodeInForUpdate(@Param("codes") Collection<String> codes);
}
