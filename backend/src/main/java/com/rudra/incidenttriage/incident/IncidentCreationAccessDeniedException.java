package com.rudra.incidenttriage.incident;

public class IncidentCreationAccessDeniedException extends RuntimeException {

	public IncidentCreationAccessDeniedException() {
		super("User is not permitted to create incidents");
	}
}
