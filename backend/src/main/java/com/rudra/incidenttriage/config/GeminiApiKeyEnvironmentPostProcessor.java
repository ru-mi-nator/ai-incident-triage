package com.rudra.incidenttriage.config;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class GeminiApiKeyEnvironmentPostProcessor
		implements EnvironmentPostProcessor, Ordered {

	static final String GEMINI_API_KEY = "GEMINI_API_KEY";
	static final String SPRING_AI_API_KEY = "spring.ai.openai.api-key";
	static final String NOT_CONFIGURED = "not-configured";

	@Override
	public void postProcessEnvironment(
			ConfigurableEnvironment environment,
			SpringApplication application
	) {
		String apiKey = environment.getProperty(GEMINI_API_KEY);
		if (apiKey == null || apiKey.isBlank()) {
			environment.getPropertySources().addFirst(new MapPropertySource(
					"geminiNoKeyDefaults",
					Map.of(SPRING_AI_API_KEY, NOT_CONFIGURED)
			));
		}
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}
}
