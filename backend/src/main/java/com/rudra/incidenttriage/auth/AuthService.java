package com.rudra.incidenttriage.auth;

import com.rudra.incidenttriage.auth.dto.AuthenticatedUserResponse;
import com.rudra.incidenttriage.auth.dto.LoginRequest;
import com.rudra.incidenttriage.auth.dto.LoginResponse;
import com.rudra.incidenttriage.config.JwtProperties;
import com.rudra.incidenttriage.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final AuthenticationManager authenticationManager;
	private final JwtTokenService jwtTokenService;
	private final JwtProperties jwtProperties;

	public LoginResponse login(LoginRequest request) {
		var authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
		);
		AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

		return new LoginResponse(
				jwtTokenService.createAccessToken(user),
				"Bearer",
				jwtProperties.expirySeconds(),
				new AuthenticatedUserResponse(user.id(), user.name(), user.username(), user.role())
		);
	}
}
