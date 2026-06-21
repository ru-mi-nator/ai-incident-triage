package com.rudra.incidenttriage.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rudra.incidenttriage.domain.entity.AiAnalysis;
import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
import com.rudra.incidenttriage.domain.enums.IncidentCategory;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import com.rudra.incidenttriage.domain.enums.IncidentStatus;
import com.rudra.incidenttriage.domain.enums.UserRole;
import com.rudra.incidenttriage.repository.AiAnalysisRepository;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class IncidentResolutionIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtEncoder jwtEncoder;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private IncidentRepository incidentRepository;

	@MockitoBean
	private AiAnalysisRepository aiAnalysisRepository;

	private User creator;
	private User assignedDeveloper;
	private User otherDeveloper;
	private Incident incident;

	@BeforeEach
	void configureResolution() {
		creator = user(1L, "Support One", "support1", UserRole.SUPPORT_ENGINEER);
		assignedDeveloper = user(3L, "Developer One", "developer1", UserRole.DEVELOPER);
		otherDeveloper = user(4L, "Developer Two", "developer2", UserRole.DEVELOPER);
		incident = Incident.open(
				"Payment API timeout",
				"Payments fail after thirty seconds.",
				ApplicationName.PAYMENT_SERVICE,
				Environment.PROD,
				"SocketTimeoutException",
				creator
		);
		incident.setId(42L);
		incident.setCreatedAt(Instant.parse("2026-06-21T10:30:00Z"));
		incident.setUpdatedAt(Instant.parse("2026-06-21T11:00:00Z"));
		incident.assignTo(assignedDeveloper, Instant.parse("2026-06-21T11:00:00Z"));

		when(userRepository.findById(3L)).thenReturn(Optional.of(assignedDeveloper));
		when(userRepository.findById(4L)).thenReturn(Optional.of(otherDeveloper));
		when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
		when(incidentRepository.findByIdForResolution(42L)).thenReturn(Optional.of(incident));
		when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(aiAnalysisRepository.findByIncidentId(42L)).thenReturn(Optional.empty());
	}

	@Test
	void assignedDeveloperResolvesAndFinalStateIsPersistedAndReturned() throws Exception {
		Instant before = Instant.now();

		resolve(42L, 3L, "developer1", "DEVELOPER", validRequest())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.title").value("Payment API timeout"))
				.andExpect(jsonPath("$.createdBy.id").value(1))
				.andExpect(jsonPath("$.assignedDeveloper.id").value(3))
				.andExpect(jsonPath("$.assignedAt").value("2026-06-21T11:00:00Z"))
				.andExpect(jsonPath("$.status").value("RESOLVED"))
				.andExpect(jsonPath("$.finalCategory").value("DATABASE"))
				.andExpect(jsonPath("$.finalPriority").value("HIGH"))
				.andExpect(jsonPath("$.actualRootCause")
						.value("Connection pool exhaustion caused by leaked connections."))
				.andExpect(jsonPath("$.actualResolution")
						.value("Closed leaked connections and added pool monitoring."))
				.andExpect(jsonPath("$.resolvedAt").isNotEmpty())
				.andExpect(jsonPath("$.aiAnalysis").value((Object) null));

		assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
		assertThat(incident.getFinalCategory()).isEqualTo(IncidentCategory.DATABASE);
		assertThat(incident.getFinalPriority()).isEqualTo(IncidentPriority.HIGH);
		assertThat(incident.getActualRootCause())
				.isEqualTo("Connection pool exhaustion caused by leaked connections.");
		assertThat(incident.getActualResolution())
				.isEqualTo("Closed leaked connections and added pool monitoring.");
		assertThat(incident.getResolvedAt()).isAfterOrEqualTo(before);
		verify(incidentRepository).save(incident);
	}

	@Test
	void resolutionTrimsTextAndIgnoresClientControlledSystemFields() throws Exception {
		Map<String, Object> request = new LinkedHashMap<>(validRequest());
		request.put("actualRootCause", "  root cause  ");
		request.put("actualResolution", "  resolution  ");
		request.put("status", "OPEN");
		request.put("assignedDeveloperId", 4);
		request.put("resolverUserId", 4);
		request.put("resolvedAt", "2000-01-01T00:00:00Z");
		request.put("incidentId", 999);
		request.put("aiAnalysis", Map.of("suggestedPriority", "LOW"));

		resolve(42L, 3L, "developer1", "DEVELOPER", request)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.status").value("RESOLVED"))
				.andExpect(jsonPath("$.assignedDeveloper.id").value(3))
				.andExpect(jsonPath("$.actualRootCause").value("root cause"))
				.andExpect(jsonPath("$.actualResolution").value("resolution"))
				.andExpect(jsonPath("$.resolvedAt").value(org.hamcrest.Matchers.not(
						"2000-01-01T00:00:00Z"
				)));
	}

	@Test
	void existingAiAnalysisRemainsUnchangedAfterResolution() throws Exception {
		AiAnalysis analysis = analysis();
		when(aiAnalysisRepository.findByIncidentId(42L)).thenReturn(Optional.of(analysis));

		resolve(42L, 3L, "developer1", "DEVELOPER", validRequest())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aiAnalysis.suggestedCategory").value("PERFORMANCE"))
				.andExpect(jsonPath("$.aiAnalysis.suggestedPriority").value("CRITICAL"))
				.andExpect(jsonPath("$.aiAnalysis.probableRootCause").value("Original AI cause"))
				.andExpect(jsonPath("$.aiAnalysis.suggestedResolution").value("Original AI resolution"));

		verify(aiAnalysisRepository, never()).save(any());
		assertThat(analysis.getProbableRootCause()).isEqualTo("Original AI cause");
	}

	@Test
	void wrongOrUnassignedDeveloperReceivesForbiddenBeforeLifecycleDisclosure() throws Exception {
		resolve(42L, 4L, "developer2", "DEVELOPER", validRequest())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
		verify(incidentRepository, never()).save(any());

		incident.setAssignedDeveloper(null);
		incident.setStatus(IncidentStatus.OPEN);
		resolve(42L, 3L, "developer1", "DEVELOPER", validRequest())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
	}

	@Test
	void openAndAlreadyResolvedIncidentsReturnNotResolvable() throws Exception {
		incident.setStatus(IncidentStatus.OPEN);
		resolve(42L, 3L, "developer1", "DEVELOPER", validRequest())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_RESOLVABLE"))
				.andExpect(jsonPath("$.message")
						.value("Incident cannot be resolved in its current state"));

		incident.setStatus(IncidentStatus.RESOLVED);
		resolve(42L, 3L, "developer1", "DEVELOPER", validRequest())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_RESOLVABLE"));
		verify(incidentRepository, never()).save(any());
	}

	@Test
	void unknownIncidentAndDeletedDatabaseUserUseStandardErrors() throws Exception {
		when(incidentRepository.findByIdForResolution(999L)).thenReturn(Optional.empty());
		resolve(999L, 3L, "developer1", "DEVELOPER", validRequest())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_FOUND"));

		when(userRepository.findById(99L)).thenReturn(Optional.empty());
		resolve(42L, 99L, "deleted", "DEVELOPER", validRequest())
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void supportEngineerAndDatabaseRoleMismatchReceiveJsonForbidden() throws Exception {
		resolve(42L, 1L, "support1", "SUPPORT_ENGINEER", validRequest())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
				.andExpect(jsonPath("$.path").value("/api/incidents/42/resolve"));
		verifyNoInteractions(incidentRepository);

		when(userRepository.findById(3L)).thenReturn(Optional.of(creator));
		resolve(42L, 3L, "developer1", "DEVELOPER", validRequest())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
	}

	@Test
	void missingTokenReturnsJsonUnauthorized() throws Exception {
		mockMvc.perform(post("/api/incidents/42/resolve")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.path").value("/api/incidents/42/resolve"));
	}

	@Test
	void missingBlankAndOversizedFieldsReturnValidationErrors() throws Exception {
		resolve(42L, 3L, "developer1", "DEVELOPER", Map.of())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.finalCategory").exists())
				.andExpect(jsonPath("$.fieldErrors.finalPriority").exists())
				.andExpect(jsonPath("$.fieldErrors.actualRootCause").exists())
				.andExpect(jsonPath("$.fieldErrors.actualResolution").exists());

		Map<String, Object> invalid = new LinkedHashMap<>(validRequest());
		invalid.put("actualRootCause", " ");
		invalid.put("actualResolution", " ");
		resolve(42L, 3L, "developer1", "DEVELOPER", invalid)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.actualRootCause")
						.value("Actual root cause must not be blank"))
				.andExpect(jsonPath("$.fieldErrors.actualResolution")
						.value("Actual resolution must not be blank"));

		invalid.put("actualRootCause", "x".repeat(2001));
		invalid.put("actualResolution", "x".repeat(3001));
		resolve(42L, 3L, "developer1", "DEVELOPER", invalid)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.actualRootCause")
						.value("Actual root cause must not exceed 2000 characters"))
				.andExpect(jsonPath("$.fieldErrors.actualResolution")
						.value("Actual resolution must not exceed 3000 characters"));
		verify(incidentRepository, never()).save(any());
	}

	@Test
	void invalidEnumsUseExistingMalformedEnumValidationContract() throws Exception {
		Map<String, Object> invalidCategory = new LinkedHashMap<>(validRequest());
		invalidCategory.put("finalCategory", "NOT_A_CATEGORY");
		String categoryResponse = resolve(
				42L,
				3L,
				"developer1",
				"DEVELOPER",
				invalidCategory
		)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.finalCategory").value("Invalid final category"))
				.andReturn().getResponse().getContentAsString();
		assertThat(categoryResponse).doesNotContain("NOT_A_CATEGORY");

		Map<String, Object> invalidPriority = new LinkedHashMap<>(validRequest());
		invalidPriority.put("finalPriority", "URGENT");
		String priorityResponse = resolve(
				42L,
				3L,
				"developer1",
				"DEVELOPER",
				invalidPriority
		)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.finalPriority").value("Invalid final priority"))
				.andReturn().getResponse().getContentAsString();
		assertThat(priorityResponse).doesNotContain("URGENT");
	}

	@Test
	void secondResolutionAttemptCannotPerformAnotherTransition() throws Exception {
		resolve(42L, 3L, "developer1", "DEVELOPER", validRequest())
				.andExpect(status().isOk());
		Instant firstResolvedAt = incident.getResolvedAt();

		resolve(42L, 3L, "developer1", "DEVELOPER", validRequest())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_RESOLVABLE"));

		assertThat(incident.getResolvedAt()).isEqualTo(firstResolvedAt);
		verify(incidentRepository, times(1)).save(incident);
	}

	@Test
	void resolutionLookupUsesDirectPessimisticWriteLock() throws Exception {
		Method method = IncidentRepository.class.getMethod("findByIdForResolution", Long.class);

		assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
		assertThat(method.getAnnotation(EntityGraph.class)).isNull();
	}

	private org.springframework.test.web.servlet.ResultActions resolve(
			long incidentId,
			long userId,
			String username,
			String role,
			Map<String, ?> request
	) throws Exception {
		return mockMvc.perform(post("/api/incidents/{id}/resolve", incidentId)
				.header("Authorization", "Bearer " + token(userId, username, role))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));
	}

	private Map<String, Object> validRequest() {
		return Map.of(
				"finalCategory", "DATABASE",
				"finalPriority", "HIGH",
				"actualRootCause", "Connection pool exhaustion caused by leaked connections.",
				"actualResolution", "Closed leaked connections and added pool monitoring."
		);
	}

	private User user(Long id, String name, String username, UserRole role) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getName()).thenReturn(name);
		when(user.getUsername()).thenReturn(username);
		when(user.getRole()).thenReturn(role);
		return user;
	}

	private AiAnalysis analysis() {
		AiAnalysis analysis = mock(AiAnalysis.class);
		when(analysis.getSuggestedCategory()).thenReturn(IncidentCategory.PERFORMANCE);
		when(analysis.getSuggestedPriority()).thenReturn(IncidentPriority.CRITICAL);
		when(analysis.getProbableRootCause()).thenReturn("Original AI cause");
		when(analysis.getSuggestedResolution()).thenReturn("Original AI resolution");
		when(analysis.getModelName()).thenReturn("test-model");
		when(analysis.getGeneratedAt()).thenReturn(Instant.parse("2026-06-21T10:45:00Z"));
		return analysis;
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
