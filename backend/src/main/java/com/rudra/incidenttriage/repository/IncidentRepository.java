package com.rudra.incidenttriage.repository;

import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.enums.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

	@Override
	@EntityGraph(attributePaths = "assignedDeveloper")
	Page<Incident> findAll(Pageable pageable);

	Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

	Page<Incident> findByCreatedById(Long createdById, Pageable pageable);

	Page<Incident> findByAssignedDeveloperId(Long assignedDeveloperId, Pageable pageable);
}
