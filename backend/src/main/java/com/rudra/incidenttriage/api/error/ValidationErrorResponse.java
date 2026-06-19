package com.rudra.incidenttriage.api.error;

import java.time.Instant;
import java.util.Map;

public record ValidationErrorResponse(
		Instant timestamp,
		int status,
		String errorCode,
		String message,
		String path,
		Map<String, String> fieldErrors
) {
}
