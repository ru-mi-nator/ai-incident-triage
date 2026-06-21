package com.rudra.incidenttriage.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import com.rudra.incidenttriage.repository.AiAnalysisRepository;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class IncidentAssignmentIntegrationTest {

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

	private User creator;
	private User developer;
	private Incident incident;

	@BeforeEach
	void configureAssignment() {
		creator = user(1L, "Support Engineer One", "support1", UserRole.SUPPORT_ENGINEER);
		developer = user(3L, "Developer One", "developer1", UserRole.DEVELOPER);
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
		incident.setUpdatedAt(Instant.parse("2026-06-21T10:30:00Z"));

		when(userRepository.findById(3L)).thenReturn(Optional.of(developer));
		when(incidentRepository.findByIdForAssignment(42L)).thenReturn(Optional.of(incident));
		when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(aiAnalysisRepository.findByIncidentId(42L)).thenReturn(Optional.empty());
	}

	@Test
	void developerAssignsOpenIncidentToJwtUserAndReceivesSafeDetails() throws Exception {
		Instant before = Instant.now();

		mockMvc.perform(post("/api/incidents/42/assign-to-me")
						.header("Authorization", "Bearer " + token(3L, "developer1", "DEVELOPER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.assignedAt").isNotEmpty())
				.andExpect(jsonPath("$.assignedDeveloper.id").value(3))
				.andExpect(jsonPath("$.assignedDeveloper.name").value("Developer One"))
				.andExpect(jsonPath("$.assignedDeveloper.username").value("developer1"))
				.andExpect(jsonPath("$.assignedDeveloper.role").value("DEVELOPER"))
				.andExpect(jsonPath("$.assignedDeveloper.password").doesNotExist());

		assertThat(incident.getAssignedDeveloper()).isSameAs(developer);
		assertThat(incident.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
		assertThat(incident.getAssignedAt()).isAfterOrEqualTo(before);
		verify(userRepository).findById(3L);
		verify(incidentRepository).findByIdForAssignment(42L);
		verify(incidentRepository).save(incident);
	}

	@Test
	void supportEngineerReceivesJsonForbiddenWithoutRepositoryCalls() throws Exception {
		mockMvc.perform(post("/api/incidents/42/assign-to-me")
						.header("Authorization", "Bearer " + token(1L, "support1", "SUPPORT_ENGINEER")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
				.andExpect(jsonPath("$.message")
						.value("You do not have permission to perform this action"))
				.andExpect(jsonPath("$.path").value("/api/incidents/42/assign-to-me"));

		verifyNoInteractions(userRepository, incidentRepository, aiAnalysisRepository);
	}

	@Test
	void missingTokenReceivesJsonUnauthorized() throws Exception {
		mockMvc.perform(post("/api/incidents/42/assign-to-me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("Authentication is required"))
				.andExpect(jsonPath("$.path").value("/api/incidents/42/assign-to-me"));
	}

	@Test
	void unknownIncidentReturnsNotFound() throws Exception {
		when(incidentRepository.findByIdForAssignment(999L)).thenReturn(Optional.empty());

		mockMvc.perform(post("/api/incidents/999/assign-to-me")
						.header("Authorization", "Bearer " + token(3L, "developer1", "DEVELOPER")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Incident not found"));

		verify(incidentRepository, never()).save(any());
	}

	@Test
	void alreadyAssignedIncidentReturnsConflictBeforeStatusCheck() throws Exception {
		incident.setAssignedDeveloper(developer);
		incident.setStatus(IncidentStatus.IN_PROGRESS);

		mockMvc.perform(post("/api/incidents/42/assign-to-me")
						.header("Authorization", "Bearer " + token(3L, "developer1", "DEVELOPER")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_ALREADY_ASSIGNED"))
				.andExpect(jsonPath("$.message").value("Incident is already assigned"));

		verify(incidentRepository, never()).save(any());
	}

	@Test
	void nonOpenUnassignedIncidentReturnsConflict() throws Exception {
		incident.setStatus(IncidentStatus.RESOLVED);

		mockMvc.perform(post("/api/incidents/42/assign-to-me")
						.header("Authorization", "Bearer " + token(3L, "developer1", "DEVELOPER")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_OPEN"))
				.andExpect(jsonPath("$.message").value("Only open incidents can be assigned"));

		verify(incidentRepository, never()).save(any());
	}

	@Test
	void deletedAuthenticatedDatabaseUserFailsSafely() throws Exception {
		when(userRepository.findById(99L)).thenReturn(Optional.empty());

		mockMvc.perform(post("/api/incidents/42/assign-to-me")
						.header("Authorization", "Bearer " + token(99L, "deleted", "DEVELOPER")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("Authentication is required"));

		verify(incidentRepository, never()).findByIdForAssignment(any());
		verify(incidentRepository, never()).save(any());
	}

	@Test
	void databaseRoleMismatchReturnsJsonForbidden() throws Exception {
		User roleMismatch = user(
				3L,
				"Support Mismatch",
				"support-mismatch",
				UserRole.SUPPORT_ENGINEER
		);
		when(userRepository.findById(3L)).thenReturn(Optional.of(roleMismatch));

		mockMvc.perform(post("/api/incidents/42/assign-to-me")
						.header("Authorization", "Bearer " + token(3L, "developer1", "DEVELOPER")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
				.andExpect(jsonPath("$.message")
						.value("You do not have permission to perform this action"));

		verify(incidentRepository, never()).findByIdForAssignment(any());
		verify(incidentRepository, never()).save(any());
	}

	@Test
	void existingAiAnalysisRemainsInAssignmentResponse() throws Exception {
		AiAnalysis analysis = mock(AiAnalysis.class);
		when(analysis.getSuggestedCategory()).thenReturn(IncidentCategory.PERFORMANCE);
		when(analysis.getSuggestedPriority()).thenReturn(IncidentPriority.HIGH);
		when(analysis.getProbableRootCause()).thenReturn("Connection pool exhaustion");
		when(analysis.getSuggestedResolution()).thenReturn("Increase connection pool capacity");
		when(analysis.getModelName()).thenReturn("test-model");
		when(analysis.getGeneratedAt()).thenReturn(Instant.parse("2026-06-21T10:35:00Z"));
		when(aiAnalysisRepository.findByIncidentId(42L)).thenReturn(Optional.of(analysis));

		mockMvc.perform(post("/api/incidents/42/assign-to-me")
						.header("Authorization", "Bearer " + token(3L, "developer1", "DEVELOPER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aiAnalysis.suggestedCategory").value("PERFORMANCE"))
				.andExpect(jsonPath("$.aiAnalysis.suggestedPriority").value("HIGH"))
				.andExpect(jsonPath("$.aiAnalysis.modelName").value("test-model"));
	}

	private User user(Long id, String name, String username, UserRole role) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getName()).thenReturn(name);
		when(user.getUsername()).thenReturn(username);
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
