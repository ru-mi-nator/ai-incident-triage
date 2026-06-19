package com.rudra.incidenttriage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
		String secret,
		String issuer,
		long expirySeconds
) {
}
