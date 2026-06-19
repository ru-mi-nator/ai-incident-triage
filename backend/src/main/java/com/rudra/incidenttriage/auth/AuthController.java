package com.rudra.incidenttriage.auth;

import java.time.Instant;

import com.rudra.incidenttriage.api.error.ApiErrorResponse;
import com.rudra.incidenttriage.auth.dto.LoginRequest;
import com.rudra.incidenttriage.auth.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiErrorResponse> invalidCredentials(
			AuthenticationException exception,
			HttpServletRequest request
	) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ApiErrorResponse(
						Instant.now(),
						HttpStatus.UNAUTHORIZED.value(),
						"INVALID_CREDENTIALS",
						"Invalid username or password",
						request.getRequestURI()
				));
	}
}
