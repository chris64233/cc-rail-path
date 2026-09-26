package com.chris64233.railpath.repository;

import com.chris64233.railpath.domain.PathReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PathReservationRepository extends JpaRepository<PathReservation, Long> {

    Optional<PathReservation> findByExternalRunNo(String externalRunNo);

    boolean existsByExternalRunNo(String externalRunNo);
}
