package com.rudra.incidenttriage.incident.dto;

import java.util.List;

import org.springframework.data.domain.Page;

public record IncidentPageResponse(
		List<IncidentSummaryResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last
) {

	public static IncidentPageResponse from(
			Page<?> page,
			List<IncidentSummaryResponse> content
	) {
		return new IncidentPageResponse(
				content,
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast()
		);
	}
}
