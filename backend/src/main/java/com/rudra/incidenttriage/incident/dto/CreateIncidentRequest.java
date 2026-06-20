package com.rudra.incidenttriage.incident.dto;

import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(
		@NotBlank(message = "Title must not be blank")
		@Size(max = 150, message = "Title must not exceed 150 characters")
		String title,

		@NotBlank(message = "Description must not be blank")
		@Size(max = 2000, message = "Description must not exceed 2000 characters")
		String description,

		@NotNull(message = "Application name must not be null")
		ApplicationName applicationName,

		@NotNull(message = "Environment must not be null")
		Environment environment,

		@Size(max = 10000, message = "Error logs must not exceed 10000 characters")
		String errorLogs
) {
}
