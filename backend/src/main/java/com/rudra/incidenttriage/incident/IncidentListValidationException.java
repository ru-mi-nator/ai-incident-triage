package com.rudra.incidenttriage.incident;

import java.util.Map;

public class IncidentListValidationException extends RuntimeException {

	private final Map<String, String> fieldErrors;

	public IncidentListValidationException(Map<String, String> fieldErrors) {
		super("Incident list parameters are invalid");
		this.fieldErrors = Map.copyOf(fieldErrors);
	}

	public Map<String, String> getFieldErrors() {
		return fieldErrors;
	}
}
