package com.rudra.incidenttriage.incident.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

class OpenAiIncidentTriageClientTest {

	@Test
	void disabledAiFailsBeforeAnyProviderCall() {
		ChatClient.Builder builder = mock(ChatClient.Builder.class);
		OpenAiIncidentTriageClient client = new OpenAiIncidentTriageClient(builder);
		ReflectionTestUtils.setField(client, "aiEnabled", false);
		ReflectionTestUtils.setField(client, "apiKey", "configured-key");

		assertThatThrownBy(() -> client.analyze(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("AI configuration is unavailable");
		verifyNoInteractions(builder);
	}

	@Test
	void blankGeminiKeyFailsBeforeAnyProviderCall() {
		ChatClient.Builder builder = mock(ChatClient.Builder.class);
		OpenAiIncidentTriageClient client = new OpenAiIncidentTriageClient(builder);
		ReflectionTestUtils.setField(client, "aiEnabled", true);
		ReflectionTestUtils.setField(client, "apiKey", " ");

		assertThatThrownBy(() -> client.analyze(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("AI configuration is unavailable");
		verifyNoInteractions(builder);
	}
}
