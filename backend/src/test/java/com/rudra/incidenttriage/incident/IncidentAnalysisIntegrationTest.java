package com.rudra.incidenttriage.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.sql.SQLException;
import java.util.Optional;

import com.rudra.incidenttriage.domain.entity.AiAnalysis;
import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
import com.rudra.incidenttriage.domain.enums.IncidentCategory;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import com.rudra.incidenttriage.domain.enums.IncidentStatus;
import com.rudra.incidenttriage.domain.enums.UserRole;
import com.rudra.incidenttriage.incident.ai.IncidentTriageAiClient;
import com.rudra.incidenttriage.incident.ai.IncidentTriageAiClient.IncidentTriageAiResult;
import com.rudra.incidenttriage.incident.ai.IncidentTriageAiClient.IncidentTriageInput;
import com.rudra.incidenttriage.repository.AiAnalysisRepository;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hibernate.exception.ConstraintViolationException;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
		"spring.ai.openai.chat.options.model=test-triage-model",
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class IncidentAnalysisIntegrationTest {

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

	@MockitoBean
	private IncidentTriageAiClient aiClient;

	private User creator;
	private User otherSupport;
	private User assignedDeveloper;
	private User otherDeveloper;
	private Incident incident;
	private IncidentTriageAiResult validResult;

	@BeforeEach
	void configureAnalysis() {
		creator = user(1L, "Support One", "support1", "creator-password", UserRole.SUPPORT_ENGINEER);
		otherSupport = user(2L, "Support Two", "support2", "other-password", UserRole.SUPPORT_ENGINEER);
		assignedDeveloper = user(3L, "Developer One", "developer1", "developer-password", UserRole.DEVELOPER);
		otherDeveloper = user(4L, "Developer Two", "developer2", "other-developer-password", UserRole.DEVELOPER);
		incident = openIncident(42L, creator);
		validResult = new IncidentTriageAiResult(
				IncidentCategory.PERFORMANCE,
				IncidentPriority.HIGH,
				"  Downstream connection pool exhaustion  ",
				"  Increase the connection pool and monitor saturation  "
		);

		when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
		when(userRepository.findById(2L)).thenReturn(Optional.of(otherSupport));
		when(userRepository.findById(3L)).thenReturn(Optional.of(assignedDeveloper));
		when(userRepository.findById(4L)).thenReturn(Optional.of(otherDeveloper));
		when(incidentRepository.findDetailsById(42L)).thenReturn(Optional.of(incident));
		when(incidentRepository.findByIdForAnalysis(42L)).thenReturn(Optional.of(incident));
		when(aiClient.analyze(any())).thenReturn(validResult);
		when(aiAnalysisRepository.saveAndFlush(any(AiAnalysis.class))).thenAnswer(invocation -> {
			AiAnalysis analysis = invocation.getArgument(0);
			analysis.setId(77L);
			analysis.setGeneratedAt(Instant.parse("2026-06-21T12:00:00Z"));
			return analysis;
		});
	}

	@Test
	void creatorSupportEngineerAnalyzesOwnOpenIncidentAndReceivesSafeStructuredDetails() throws Exception {
		String response = analyze(42L, 1L, "support1", "SUPPORT_ENGINEER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.aiAnalysis.suggestedCategory").value("PERFORMANCE"))
				.andExpect(jsonPath("$.aiAnalysis.suggestedPriority").value("HIGH"))
				.andExpect(jsonPath("$.aiAnalysis.probableRootCause")
						.value("Downstream connection pool exhaustion"))
				.andExpect(jsonPath("$.aiAnalysis.suggestedResolution")
						.value("Increase the connection pool and monitor saturation"))
				.andExpect(jsonPath("$.aiAnalysis.modelName").value("test-triage-model"))
				.andExpect(jsonPath("$.aiAnalysis.generatedAt").value("2026-06-21T12:00:00Z"))
				.andExpect(jsonPath("$.createdBy.password").doesNotExist())
				.andExpect(jsonPath("$.aiAnalysis.incident").doesNotExist())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(response).doesNotContain("creator-password");
		verify(userRepository, times(2)).findById(1L);
		verify(aiAnalysisRepository, times(2)).existsByIncidentId(42L);
		verify(incidentRepository).findByIdForAnalysis(42L);

		ArgumentCaptor<IncidentTriageInput> input = ArgumentCaptor.forClass(IncidentTriageInput.class);
		verify(aiClient).analyze(input.capture());
		assertThat(input.getValue()).isEqualTo(new IncidentTriageInput(
				"Payment API timeout",
				"Payments fail after thirty seconds.",
				ApplicationName.PAYMENT_SERVICE,
				Environment.PROD,
				"SocketTimeoutException"
		));

		ArgumentCaptor<AiAnalysis> analysis = ArgumentCaptor.forClass(AiAnalysis.class);
		verify(aiAnalysisRepository).saveAndFlush(analysis.capture());
		assertThat(analysis.getValue().getIncident()).isSameAs(incident);
		assertThat(analysis.getValue().getModelName()).isEqualTo("test-triage-model");
	}

	@Test
	void assignedDeveloperAnalyzesOwnInProgressIncident() throws Exception {
		incident.assignTo(assignedDeveloper, Instant.parse("2026-06-21T11:00:00Z"));

		analyze(42L, 3L, "developer1", "DEVELOPER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.assignedDeveloper.id").value(3))
				.andExpect(jsonPath("$.aiAnalysis.suggestedPriority").value("HIGH"));
	}

	@Test
	void anotherSupportEngineerReceivesForbiddenWithoutCallingAi() throws Exception {
		analyze(42L, 2L, "support2", "SUPPORT_ENGINEER")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

		verifyNoInteractions(aiClient);
		verify(aiAnalysisRepository, never()).saveAndFlush(any());
	}

	@Test
	void anotherDeveloperReceivesForbiddenWithoutCallingAi() throws Exception {
		incident.assignTo(assignedDeveloper, Instant.parse("2026-06-21T11:00:00Z"));

		analyze(42L, 4L, "developer2", "DEVELOPER")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

		verifyNoInteractions(aiClient);
		verify(aiAnalysisRepository, never()).saveAndFlush(any());
	}

	@Test
	void supportEngineerCannotAnalyzeAssignedIncident() throws Exception {
		incident.assignTo(assignedDeveloper, Instant.parse("2026-06-21T11:00:00Z"));

		analyze(42L, 1L, "support1", "SUPPORT_ENGINEER")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_ANALYZABLE"));

		verifyNoInteractions(aiClient);
	}

	@Test
	void supportEngineerAndDeveloperCannotAnalyzeInvalidStates() throws Exception {
		incident.setStatus(IncidentStatus.RESOLVED);
		analyze(42L, 1L, "support1", "SUPPORT_ENGINEER")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_ANALYZABLE"));

		incident.setStatus(IncidentStatus.OPEN);
		analyze(42L, 3L, "developer1", "DEVELOPER")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_ANALYZABLE"));

		verifyNoInteractions(aiClient);
	}

	@Test
	void developerCannotAnalyzeAssignedIncidentWhileItIsOpen() throws Exception {
		incident.setAssignedDeveloper(assignedDeveloper);
		incident.setStatus(IncidentStatus.OPEN);

		analyze(42L, 3L, "developer1", "DEVELOPER")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_ANALYZABLE"));

		verifyNoInteractions(aiClient);
	}

	@Test
	void existingAnalysisReturnsConflictBeforeCallingAi() throws Exception {
		when(aiAnalysisRepository.existsByIncidentId(42L)).thenReturn(true);

		analyze(42L, 1L, "support1", "SUPPORT_ENGINEER")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("AI_ANALYSIS_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message")
						.value("AI analysis already exists for this incident"));

		verifyNoInteractions(aiClient);
		verify(aiAnalysisRepository, never()).saveAndFlush(any());
	}

	@Test
	void wrongOwnerReceivesForbiddenEvenWhenAnalysisAlreadyExists() throws Exception {
		when(aiAnalysisRepository.existsByIncidentId(42L)).thenReturn(true);

		analyze(42L, 2L, "support2", "SUPPORT_ENGINEER")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

		verify(aiAnalysisRepository, never()).existsByIncidentId(42L);
		verifyNoInteractions(aiClient);
	}

	@Test
	void unknownIncidentAndMissingTokenUseStandardJsonErrors() throws Exception {
		when(incidentRepository.findDetailsById(999L)).thenReturn(Optional.empty());

		analyze(999L, 1L, "support1", "SUPPORT_ENGINEER")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_FOUND"));

		mockMvc.perform(post("/api/incidents/42/analyze"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.path").value("/api/incidents/42/analyze"));
	}

	@Test
	void aiFailureAndInvalidOutputReturnUnavailableWithoutSaving() throws Exception {
		when(aiClient.analyze(any())).thenThrow(new IllegalStateException("provider detail"));

		String failure = analyze(42L, 1L, "support1", "SUPPORT_ENGINEER")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errorCode").value("AI_SERVICE_UNAVAILABLE"))
				.andExpect(jsonPath("$.message").value("AI analysis is temporarily unavailable"))
				.andReturn()
				.getResponse()
				.getContentAsString();
		assertThat(failure).doesNotContain("provider detail");
		verify(aiAnalysisRepository, never()).saveAndFlush(any());

		doReturn(new IncidentTriageAiResult(
				null,
				IncidentPriority.HIGH,
				"cause",
				"resolution"
		)).when(aiClient).analyze(any());
		analyze(42L, 1L, "support1", "SUPPORT_ENGINEER")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errorCode").value("AI_SERVICE_UNAVAILABLE"));
		verify(aiAnalysisRepository, never()).saveAndFlush(any());
	}

	@Test
	void markdownOrOversizedOutputIsRejectedWithoutSaving() throws Exception {
		when(aiClient.analyze(any())).thenReturn(new IncidentTriageAiResult(
				IncidentCategory.API,
				IncidentPriority.MEDIUM,
				"```wrapped```",
				"x".repeat(3001)
		));

		analyze(42L, 1L, "support1", "SUPPORT_ENGINEER")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errorCode").value("AI_SERVICE_UNAVAILABLE"));

		verify(aiAnalysisRepository, never()).saveAndFlush(any());
	}

	@Test
	void changedStateAfterAiCallIsRevalidatedAndNotSaved() throws Exception {
		doAnswer(invocation -> {
			incident.setStatus(IncidentStatus.RESOLVED);
			return validResult;
		}).when(aiClient).analyze(any());

		analyze(42L, 1L, "support1", "SUPPORT_ENGINEER")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_ANALYZABLE"));

		verify(incidentRepository).findByIdForAnalysis(42L);
		verify(aiAnalysisRepository, never()).saveAndFlush(any());
	}

	@Test
	void analysisCreatedDuringAiCallIsRecheckedAfterLockedLookup() throws Exception {
		when(aiAnalysisRepository.existsByIncidentId(42L)).thenReturn(false, true);

		analyze(42L, 1L, "support1", "SUPPORT_ENGINEER")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("AI_ANALYSIS_ALREADY_EXISTS"));

		verify(aiClient).analyze(any());
		verify(incidentRepository).findByIdForAnalysis(42L);
		verify(aiAnalysisRepository, times(2)).existsByIncidentId(42L);
		verify(aiAnalysisRepository, never()).saveAndFlush(any());
	}

	@Test
	void concurrentUniqueConstraintFailureReturnsAlreadyExists() throws Exception {
		ConstraintViolationException uniqueConstraintFailure = new ConstraintViolationException(
				"duplicate analysis",
				new SQLException("unique violation", "23505"),
				"uq_ai_analyses_incident_id"
		);
		when(aiAnalysisRepository.saveAndFlush(any(AiAnalysis.class)))
				.thenThrow(new DataIntegrityViolationException(
						"constraint detail",
						uniqueConstraintFailure
				));

		String response = analyze(42L, 1L, "support1", "SUPPORT_ENGINEER")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("AI_ANALYSIS_ALREADY_EXISTS"))
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(response).doesNotContain("constraint detail");
	}

	private org.springframework.test.web.servlet.ResultActions analyze(
			long incidentId,
			long userId,
			String username,
			String role
	) throws Exception {
		return mockMvc.perform(post("/api/incidents/{id}/analyze", incidentId)
				.header("Authorization", "Bearer " + token(userId, username, role)));
	}

	private Incident openIncident(Long id, User createdBy) {
		Incident incident = Incident.open(
				"Payment API timeout",
				"Payments fail after thirty seconds.",
				ApplicationName.PAYMENT_SERVICE,
				Environment.PROD,
				"SocketTimeoutException",
				createdBy
		);
		incident.setId(id);
		incident.setCreatedAt(Instant.parse("2026-06-21T10:30:00Z"));
		incident.setUpdatedAt(Instant.parse("2026-06-21T10:30:00Z"));
		return incident;
	}

	private User user(
			Long id,
			String name,
			String username,
			String password,
			UserRole role
	) {
		User user = org.mockito.Mockito.mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getName()).thenReturn(name);
		when(user.getUsername()).thenReturn(username);
		when(user.getPassword()).thenReturn(password);
		when(user.getRole()).thenReturn(role);
		return user;
	}

	private String token(long userId, String username, String role) {
		Instant issuedAt = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("ai-incident-triage")
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plusSeconds(3600))
				.subject(username)
				.claim("userId", userId)
				.claim("role", role)
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(),
				claims
		)).getTokenValue();
	}
}
