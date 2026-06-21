package com.rudra.incidenttriage.repository;

import java.util.Optional;

import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.enums.IncidentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

	@Override
	@EntityGraph(attributePaths = "assignedDeveloper")
	Page<Incident> findAll(Pageable pageable);

	@EntityGraph(attributePaths = {"createdBy", "assignedDeveloper"})
	@Query("select incident from Incident incident where incident.id = :id")
	Optional<Incident> findDetailsById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = {"createdBy", "assignedDeveloper"})
	@Query("select incident from Incident incident where incident.id = :id")
	Optional<Incident> findByIdForAssignment(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = {"createdBy", "assignedDeveloper"})
	@Query("select incident from Incident incident where incident.id = :id")
	Optional<Incident> findByIdForAnalysis(@Param("id") Long id);

	Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

	Page<Incident> findByCreatedById(Long createdById, Pageable pageable);

	Page<Incident> findByAssignedDeveloperId(Long assignedDeveloperId, Pageable pageable);
}
