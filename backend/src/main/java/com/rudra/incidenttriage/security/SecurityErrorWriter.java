package com.rudra.incidenttriage.security;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rudra.incidenttriage.api.error.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

	private final ObjectMapper objectMapper;

	public void write(
			HttpServletResponse response,
			int status,
			String errorCode,
			String message,
			String path
	) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(
				response.getOutputStream(),
				new ApiErrorResponse(Instant.now(), status, errorCode, message, path)
		);
	}
}
