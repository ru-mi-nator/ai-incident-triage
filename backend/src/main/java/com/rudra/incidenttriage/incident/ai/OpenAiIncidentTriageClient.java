package com.rudra.incidenttriage.incident.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenAiIncidentTriageClient implements IncidentTriageAiClient {

	private static final String SYSTEM_PROMPT = """
			You are an incident-triage assistant. Analyze the supplied software incident and return
			a structured suggestion for human review, never an automatic final decision.
			Use only these category values: API, DATABASE, AUTHENTICATION, DEPLOYMENT, PERFORMANCE,
			NETWORK, INTEGRATION, CONFIGURATION, OTHER.
			Use only these priority values: LOW, MEDIUM, HIGH, CRITICAL.
			Return concise plain text for probableRootCause and suggestedResolution without Markdown.
			""";

	private final ChatClient.Builder chatClientBuilder;

	@Value("${ai.enabled:false}")
	private boolean aiEnabled;

	@Value("${spring.ai.openai.api-key}")
	private String apiKey;

	@Override
	public IncidentTriageAiResult analyze(IncidentTriageInput input) {
		if (!aiEnabled || "not-configured".equals(apiKey) || apiKey.isBlank()) {
			throw new IllegalStateException("AI configuration is unavailable");
		}

		return chatClientBuilder.build()
				.prompt()
				.system(SYSTEM_PROMPT)
				.user(buildUserPrompt(input))
				.call()
				.entity(IncidentTriageAiResult.class);
	}

	private String buildUserPrompt(IncidentTriageInput input) {
		String errorLogs = input.errorLogs() == null || input.errorLogs().isBlank()
				? "Not provided"
				: input.errorLogs();
		return """
				Title: %s
				Description: %s
				Application: %s
				Environment: %s
				Error logs: %s
				""".formatted(
				input.title(),
				input.description(),
				input.applicationName(),
				input.environment(),
				errorLogs
		);
	}
}
