package com.rudra.incidenttriage.api.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.rudra.incidenttriage.incident.AuthenticatedUserNotFoundException;
import com.rudra.incidenttriage.incident.IncidentCreationAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<?> handleUnreadableRequestBody(
			HttpMessageNotReadableException exception,
			HttpServletRequest request
	) {
		InvalidFormatException invalidFormatException = findInvalidFormatException(exception);
		if (invalidFormatException != null && invalidFormatException.getTargetType().isEnum()) {
			String field = lastFieldName(invalidFormatException.getPath());
			if (field != null) {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST)
						.body(new ValidationErrorResponse(
								Instant.now(),
								HttpStatus.BAD_REQUEST.value(),
								"VALIDATION_FAILED",
								"Request validation failed",
								request.getRequestURI(),
								Map.of(field, invalidEnumMessage(field))
						));
			}
		}

		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiErrorResponse(
						Instant.now(),
						HttpStatus.BAD_REQUEST.value(),
						"INVALID_REQUEST_BODY",
						"Request body is invalid",
						request.getRequestURI()
				));
	}

	@ExceptionHandler(AuthenticatedUserNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleAuthenticatedUserNotFound(
			AuthenticatedUserNotFoundException exception,
			HttpServletRequest request
	) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ApiErrorResponse(
						Instant.now(),
						HttpStatus.UNAUTHORIZED.value(),
						"UNAUTHORIZED",
						"Authentication is required",
						request.getRequestURI()
				));
	}

	@ExceptionHandler(IncidentCreationAccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleIncidentCreationAccessDenied(
			IncidentCreationAccessDeniedException exception,
			HttpServletRequest request
	) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ApiErrorResponse(
						Instant.now(),
						HttpStatus.FORBIDDEN.value(),
						"ACCESS_DENIED",
						"You do not have permission to perform this action",
						request.getRequestURI()
				));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ValidationErrorResponse> handleValidationFailure(
			MethodArgumentNotValidException exception,
			HttpServletRequest request
	) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage())
		);

		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ValidationErrorResponse(
						Instant.now(),
						HttpStatus.BAD_REQUEST.value(),
						"VALIDATION_FAILED",
						"Request validation failed",
						request.getRequestURI(),
						fieldErrors
				));
	}

	private InvalidFormatException findInvalidFormatException(Throwable exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof InvalidFormatException invalidFormatException) {
				return invalidFormatException;
			}
			cause = cause.getCause();
		}
		return null;
	}

	private String lastFieldName(List<JsonMappingException.Reference> path) {
		if (path.isEmpty()) {
			return null;
		}
		return path.get(path.size() - 1).getFieldName();
	}

	private String invalidEnumMessage(String field) {
		String words = field.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
		return "Invalid " + words;
	}
}
