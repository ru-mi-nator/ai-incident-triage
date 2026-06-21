package com.rudra.incidenttriage.incident.dto;

import java.time.Instant;

import com.rudra.incidenttriage.domain.entity.AiAnalysis;
import com.rudra.incidenttriage.domain.enums.IncidentCategory;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;

public record AiAnalysisResponse(
		IncidentCategory suggestedCategory,
		IncidentPriority suggestedPriority,
		String probableRootCause,
		String suggestedResolution,
		String modelName,
		Instant generatedAt
) {

	public static AiAnalysisResponse from(AiAnalysis analysis) {
		return new AiAnalysisResponse(
				analysis.getSuggestedCategory(),
				analysis.getSuggestedPriority(),
				analysis.getProbableRootCause(),
				analysis.getSuggestedResolution(),
				analysis.getModelName(),
				analysis.getGeneratedAt()
		);
	}
}
