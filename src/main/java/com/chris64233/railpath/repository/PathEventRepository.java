package com.chris64233.railpath.repository;

import com.chris64233.railpath.domain.PathEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PathEventRepository extends JpaRepository<PathEvent, Long> {

    List<PathEvent> findByExternalRunNoOrderByIdAsc(String externalRunNo);

    List<PathEvent> findAllByOrderByIdAsc();
}
