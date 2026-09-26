package com.chris64233.railpath.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.railpath.domain.PathLeg;

public interface PathLegRepository extends JpaRepository<PathLeg, Long> {

    @Query("select l from PathLeg l join fetch l.section join fetch l.path where l.section.id in :sectionIds")
    List<PathLeg> findBySectionIdIn(@Param("sectionIds") Collection<Long> sectionIds);
}
