package com.rudra.incidenttriage.incident.dto;

import java.time.Instant;

import com.rudra.incidenttriage.domain.entity.AiAnalysis;
import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
import com.rudra.incidenttriage.domain.enums.IncidentCategory;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import com.rudra.incidenttriage.domain.enums.IncidentStatus;

public record IncidentDetailsResponse(
		Long id,
		String displayId,
		String title,
		String description,
		ApplicationName applicationName,
		Environment environment,
		String errorLogs,
		IncidentStatus status,
		IncidentUserResponse createdBy,
		IncidentUserResponse assignedDeveloper,
		Instant assignedAt,
		IncidentCategory finalCategory,
		IncidentPriority finalPriority,
		String actualRootCause,
		String actualResolution,
		Instant resolvedAt,
		Instant createdAt,
		Instant updatedAt,
		AiAnalysisResponse aiAnalysis
) {

	public static IncidentDetailsResponse from(Incident incident) {
		return from(incident, null);
	}

	public static IncidentDetailsResponse from(Incident incident, AiAnalysis analysis) {
		return new IncidentDetailsResponse(
				incident.getId(),
				"INC-%04d".formatted(incident.getId()),
				incident.getTitle(),
				incident.getDescription(),
				incident.getApplicationName(),
				incident.getEnvironment(),
				incident.getErrorLogs(),
				incident.getStatus(),
				IncidentUserResponse.from(incident.getCreatedBy()),
				incident.getAssignedDeveloper() == null
						? null
						: IncidentUserResponse.from(incident.getAssignedDeveloper()),
				incident.getAssignedAt(),
				incident.getFinalCategory(),
				incident.getFinalPriority(),
				incident.getActualRootCause(),
				incident.getActualResolution(),
				incident.getResolvedAt(),
				incident.getCreatedAt(),
				incident.getUpdatedAt(),
				analysis == null ? null : AiAnalysisResponse.from(analysis)
		);
	}
}
