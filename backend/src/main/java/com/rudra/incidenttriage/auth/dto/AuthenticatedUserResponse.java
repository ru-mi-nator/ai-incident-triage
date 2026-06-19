package com.rudra.incidenttriage.auth.dto;

import com.rudra.incidenttriage.domain.enums.UserRole;

public record AuthenticatedUserResponse(
		Long id,
		String name,
		String username,
		UserRole role
) {
}
