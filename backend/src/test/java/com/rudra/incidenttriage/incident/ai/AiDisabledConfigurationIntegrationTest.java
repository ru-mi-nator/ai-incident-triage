package com.rudra.incidenttriage.incident.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.UserRole;
import com.rudra.incidenttriage.repository.AiAnalysisRepository;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
		"ai.enabled=false",
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class AiDisabledConfigurationIntegrationTest {

	@Autowired
	private IncidentTriageAiClient aiClient;

	@Autowired
	private Environment environment;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private IncidentRepository incidentRepository;

	@MockitoBean
	private AiAnalysisRepository aiAnalysisRepository;

	@Test
	void applicationStartsWithAiDisabledAndNoGeminiKey() {
		assertThat(aiClient).isInstanceOf(OpenAiIncidentTriageClient.class);
		assertThat(environment.getProperty("ai.enabled", Boolean.class)).isFalse();
		assertThat(environment.getProperty("spring.ai.openai.api-key"))
				.isEqualTo("not-configured");
		assertThat(environment.getProperty("spring.ai.openai.base-url"))
				.isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai");
		assertThat(environment.getProperty("spring.ai.openai.chat.completions-path"))
				.isEqualTo("/chat/completions");
		assertThat(environment.getProperty("spring.ai.openai.chat.options.model"))
				.isEqualTo("gemini-2.5-flash-lite");
		assertThat(environment.getProperty("spring.ai.openai.base-url")
				+ environment.getProperty("spring.ai.openai.chat.completions-path"))
				.isEqualTo(
						"https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
				);
	}

	@Test
	void analyzeWithAiDisabledReturnsSanitizedUnavailableResponse() throws Exception {
		User creator = org.mockito.Mockito.mock(User.class);
		when(creator.getId()).thenReturn(1L);
		when(creator.getRole()).thenReturn(UserRole.SUPPORT_ENGINEER);
		Incident incident = Incident.open(
				"Payment API timeout",
				"Payments fail after thirty seconds.",
				ApplicationName.PAYMENT_SERVICE,
				com.rudra.incidenttriage.domain.enums.Environment.PROD,
				"SocketTimeoutException",
				creator
		);
		incident.setId(42L);
		incident.setCreatedAt(Instant.parse("2026-06-21T10:30:00Z"));
		incident.setUpdatedAt(Instant.parse("2026-06-21T10:30:00Z"));

		when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
		when(incidentRepository.findDetailsById(42L)).thenReturn(Optional.of(incident));
		when(aiAnalysisRepository.existsByIncidentId(42L)).thenReturn(false);

		mockMvc.perform(post("/api/incidents/42/analyze")
						.header("Authorization", "Bearer " + token()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errorCode").value("AI_SERVICE_UNAVAILABLE"))
				.andExpect(jsonPath("$.message")
						.value("AI analysis is temporarily unavailable"));

		verify(aiAnalysisRepository, never()).saveAndFlush(any());
		verify(incidentRepository, never()).findByIdForAnalysis(any());
	}

	private String token() {
		Instant issuedAt = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("ai-incident-triage")
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plusSeconds(3600))
				.subject("support1")
				.claim("userId", 1L)
				.claim("role", "SUPPORT_ENGINEER")
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(),
				claims
		)).getTokenValue();
	}
}
