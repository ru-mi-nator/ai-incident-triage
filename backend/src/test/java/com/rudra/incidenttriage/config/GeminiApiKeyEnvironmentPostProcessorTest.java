package com.rudra.incidenttriage.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class GeminiApiKeyEnvironmentPostProcessorTest {

	private final GeminiApiKeyEnvironmentPostProcessor processor =
			new GeminiApiKeyEnvironmentPostProcessor();

	@Test
	void blankGeminiKeyUsesNonSecretStartupSentinel() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty(GeminiApiKeyEnvironmentPostProcessor.GEMINI_API_KEY, " ");

		processor.postProcessEnvironment(environment, new SpringApplication());

		assertThat(environment.getProperty(
				GeminiApiKeyEnvironmentPostProcessor.SPRING_AI_API_KEY
		)).isEqualTo(GeminiApiKeyEnvironmentPostProcessor.NOT_CONFIGURED);
	}

	@Test
	void configuredGeminiKeyIsNotReplaced() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty(GeminiApiKeyEnvironmentPostProcessor.GEMINI_API_KEY, "configured");

		processor.postProcessEnvironment(environment, new SpringApplication());

		assertThat(environment.getProperty(
				GeminiApiKeyEnvironmentPostProcessor.SPRING_AI_API_KEY
		)).isNull();
	}
}
