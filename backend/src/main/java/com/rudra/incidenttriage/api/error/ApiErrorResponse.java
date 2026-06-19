package com.rudra.incidenttriage.api.error;

import java.time.Instant;

public record ApiErrorResponse(
		Instant timestamp,
		int status,
		String errorCode,
		String message,
		String path
) {
}
