package com.rudra.incidenttriage.incident.dto;

import java.time.Instant;

import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
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
		IncidentUserResponse creator,
		IncidentUserResponse assignedDeveloper,
		Instant createdAt,
		Instant updatedAt
) {

	public static IncidentDetailsResponse from(Incident incident) {
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
				incident.getCreatedAt(),
				incident.getUpdatedAt()
		);
	}
}
