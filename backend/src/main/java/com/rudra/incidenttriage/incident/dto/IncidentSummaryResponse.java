package com.rudra.incidenttriage.incident.dto;

import java.time.Instant;

import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import com.rudra.incidenttriage.domain.enums.IncidentStatus;

public record IncidentSummaryResponse(
		Long id,
		String displayId,
		String title,
		ApplicationName applicationName,
		Environment environment,
		IncidentStatus status,
		IncidentPriority priority,
		IncidentUserResponse assignedDeveloper,
		Instant createdAt
) {

	public static IncidentSummaryResponse from(Incident incident, IncidentPriority priority) {
		return new IncidentSummaryResponse(
				incident.getId(),
				"INC-%04d".formatted(incident.getId()),
				incident.getTitle(),
				incident.getApplicationName(),
				incident.getEnvironment(),
				incident.getStatus(),
				priority,
				incident.getAssignedDeveloper() == null
						? null
						: IncidentUserResponse.from(incident.getAssignedDeveloper()),
				incident.getCreatedAt()
		);
	}
}
