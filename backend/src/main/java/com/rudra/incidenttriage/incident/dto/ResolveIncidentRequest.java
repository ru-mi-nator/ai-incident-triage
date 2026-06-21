package com.rudra.incidenttriage.incident.dto;

import com.rudra.incidenttriage.domain.enums.IncidentCategory;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveIncidentRequest(
		@NotNull(message = "Final category must not be null")
		IncidentCategory finalCategory,

		@NotNull(message = "Final priority must not be null")
		IncidentPriority finalPriority,

		@NotBlank(message = "Actual root cause must not be blank")
		@Size(max = 2000, message = "Actual root cause must not exceed 2000 characters")
		String actualRootCause,

		@NotBlank(message = "Actual resolution must not be blank")
		@Size(max = 3000, message = "Actual resolution must not exceed 3000 characters")
		String actualResolution
) {
}
