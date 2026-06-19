package com.rudra.incidenttriage.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@NotBlank(message = "Username must not be blank")
		String username,
		@NotBlank(message = "Password must not be blank")
		String password
) {

	@Override
	public String toString() {
		return "LoginRequest[username=" + username + ", password=[REDACTED]]";
	}
}
