package com.rudra.incidenttriage.incident;

public class AuthenticatedUserNotFoundException extends RuntimeException {

	public AuthenticatedUserNotFoundException() {
		super("Authenticated user no longer exists");
	}
}
