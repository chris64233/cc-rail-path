package com.chris64233.railpath.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.railpath.domain.PathEvent;

public interface PathEventRepository extends JpaRepository<PathEvent, Long> {

    List<PathEvent> findByPathIdOrderByIdAsc(Long pathId);
}
