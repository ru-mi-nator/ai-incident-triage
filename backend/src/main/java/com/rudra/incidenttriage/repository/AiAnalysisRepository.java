package com.rudra.incidenttriage.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.rudra.incidenttriage.domain.entity.AiAnalysis;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

	interface IncidentPriorityProjection {

		Long getIncidentId();

		IncidentPriority getSuggestedPriority();
	}

	Optional<AiAnalysis> findByIncidentId(Long incidentId);

	boolean existsByIncidentId(Long incidentId);

	@Query("""
			select analysis.incident.id as incidentId,
					analysis.suggestedPriority as suggestedPriority
			from AiAnalysis analysis
			where analysis.incident.id in :incidentIds
			""")
	List<IncidentPriorityProjection> findPrioritiesByIncidentIds(
			@Param("incidentIds") Collection<Long> incidentIds
	);
}
