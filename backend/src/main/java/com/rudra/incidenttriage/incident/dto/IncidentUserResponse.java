package com.rudra.incidenttriage.incident.dto;

import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.UserRole;

public record IncidentUserResponse(
		Long id,
		String name,
		String username,
		UserRole role
) {

	public static IncidentUserResponse from(User user) {
		return new IncidentUserResponse(
				user.getId(),
				user.getName(),
				user.getUsername(),
				user.getRole()
		);
	}
}
