package com.rudra.incidenttriage.incident.ai;

import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
import com.rudra.incidenttriage.domain.enums.IncidentCategory;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;

public interface IncidentTriageAiClient {

	IncidentTriageAiResult analyze(IncidentTriageInput input);

	record IncidentTriageInput(
			String title,
			String description,
			ApplicationName applicationName,
			Environment environment,
			String errorLogs
	) {
	}

	record IncidentTriageAiResult(
			IncidentCategory suggestedCategory,
			IncidentPriority suggestedPriority,
			String probableRootCause,
			String suggestedResolution
	) {
	}
}
