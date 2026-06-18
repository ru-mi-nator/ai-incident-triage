package com.rudra.incidenttriage.repository;

import java.util.Optional;

import com.rudra.incidenttriage.domain.entity.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

	Optional<AiAnalysis> findByIncidentId(Long incidentId);

	boolean existsByIncidentId(Long incidentId);
}
