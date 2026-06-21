package com.rudra.incidenttriage.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
		"security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class IncidentDetailsIntegrationTest {

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

	private Incident incident;
	private User creator;

	@BeforeEach
	void configureDetails() {
		creator = user(
				1L,
				"Support Engineer One",
				"support1",
				"creator-password-hash",
				UserRole.SUPPORT_ENGINEER
		);
		incident = incident(42L, creator, null);
		when(incidentRepository.findDetailsById(42L)).thenReturn(Optional.of(incident));
		when(aiAnalysisRepository.findByIncidentId(42L)).thenReturn(Optional.empty());
	}

	@Test
	void supportEngineerCanRetrieveCompleteIncidentDetails() throws Exception {
		performDetails("SUPPORT_ENGINEER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.displayId").value("INC-0042"))
				.andExpect(jsonPath("$.title").value("Payment API timeout"))
				.andExpect(jsonPath("$.description").value("Payments fail after thirty seconds."))
				.andExpect(jsonPath("$.applicationName").value("PAYMENT_SERVICE"))
				.andExpect(jsonPath("$.environment").value("PROD"))
				.andExpect(jsonPath("$.errorLogs").value("SocketTimeoutException"))
				.andExpect(jsonPath("$.status").value("RESOLVED"))
				.andExpect(jsonPath("$.createdBy.id").value(1))
				.andExpect(jsonPath("$.createdBy.name").value("Support Engineer One"))
				.andExpect(jsonPath("$.createdBy.username").value("support1"))
				.andExpect(jsonPath("$.createdBy.role").value("SUPPORT_ENGINEER"))
				.andExpect(jsonPath("$.assignedDeveloper").value((Object) null))
				.andExpect(jsonPath("$.assignedAt").value("2026-06-21T10:40:00Z"))
				.andExpect(jsonPath("$.finalCategory").value("PERFORMANCE"))
				.andExpect(jsonPath("$.finalPriority").value("CRITICAL"))
				.andExpect(jsonPath("$.actualRootCause").value("Connection pool exhaustion"))
				.andExpect(jsonPath("$.actualResolution").value("Increased the pool size"))
				.andExpect(jsonPath("$.resolvedAt").value("2026-06-21T11:30:00Z"))
				.andExpect(jsonPath("$.createdAt").value("2026-06-21T10:30:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-06-21T11:30:00Z"))
				.andExpect(jsonPath("$.aiAnalysis").value((Object) null));

		verify(incidentRepository).findDetailsById(42L);
		verify(aiAnalysisRepository).findByIncidentId(42L);
	}

	@Test
	void developerCanRetrieveIncidentDetails() throws Exception {
		performDetails("DEVELOPER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42));
	}

	@Test
	void missingTokenReturnsJsonUnauthorized() throws Exception {
		mockMvc.perform(get("/api/incidents/42"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("Authentication is required"))
				.andExpect(jsonPath("$.path").value("/api/incidents/42"));

		verifyNoInteractions(incidentRepository, aiAnalysisRepository);
	}

	@Test
	void unknownIncidentReturnsStandardJsonNotFound() throws Exception {
		when(incidentRepository.findDetailsById(999L)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/incidents/999")
						.header("Authorization", "Bearer " + token("SUPPORT_ENGINEER")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Incident not found"))
				.andExpect(jsonPath("$.path").value("/api/incidents/999"));
	}

	@Test
	void assignedDeveloperAndAiAnalysisAreSafelyMappedWhenPresent() throws Exception {
		User developer = user(
				3L,
				"Developer One",
				"developer1",
				"developer-password-hash",
				UserRole.DEVELOPER
		);
		when(incident.getAssignedDeveloper()).thenReturn(developer);
		AiAnalysis analysis = analysis();
		when(aiAnalysisRepository.findByIncidentId(42L)).thenReturn(Optional.of(analysis));

		String response = performDetails("DEVELOPER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assignedDeveloper.id").value(3))
				.andExpect(jsonPath("$.assignedDeveloper.name").value("Developer One"))
				.andExpect(jsonPath("$.assignedDeveloper.username").value("developer1"))
				.andExpect(jsonPath("$.assignedDeveloper.role").value("DEVELOPER"))
				.andExpect(jsonPath("$.aiAnalysis.suggestedCategory").value("PERFORMANCE"))
				.andExpect(jsonPath("$.aiAnalysis.suggestedPriority").value("HIGH"))
				.andExpect(jsonPath("$.aiAnalysis.probableRootCause")
						.value("Downstream connection pool exhaustion"))
				.andExpect(jsonPath("$.aiAnalysis.suggestedResolution")
						.value("Increase the connection pool and monitor saturation"))
				.andExpect(jsonPath("$.aiAnalysis.modelName").value("test-model"))
				.andExpect(jsonPath("$.aiAnalysis.generatedAt").value("2026-06-21T10:35:00Z"))
				.andExpect(jsonPath("$.createdBy.password").doesNotExist())
				.andExpect(jsonPath("$.assignedDeveloper.password").doesNotExist())
				.andExpect(jsonPath("$.aiAnalysis.incident").doesNotExist())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(response)
				.doesNotContain("creator-password-hash")
				.doesNotContain("developer-password-hash")
				.doesNotContain("hibernateLazyInitializer");
	}

	@Test
	void displayIdFormattingWorksAbove9999() throws Exception {
		Incident largeIdIncident = incident(10000L, creator, null);
		when(incidentRepository.findDetailsById(10000L)).thenReturn(Optional.of(largeIdIncident));
		when(aiAnalysisRepository.findByIncidentId(10000L)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/incidents/10000")
						.header("Authorization", "Bearer " + token("SUPPORT_ENGINEER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayId").value("INC-10000"));
	}

	private org.springframework.test.web.servlet.ResultActions performDetails(String role) throws Exception {
		return mockMvc.perform(get("/api/incidents/42")
				.header("Authorization", "Bearer " + token(role)));
	}

	private Incident incident(Long id, User createdBy, User assignedDeveloper) {
		Incident incident = mock(Incident.class);
		when(incident.getId()).thenReturn(id);
		when(incident.getTitle()).thenReturn("Payment API timeout");
		when(incident.getDescription()).thenReturn("Payments fail after thirty seconds.");
		when(incident.getApplicationName()).thenReturn(ApplicationName.PAYMENT_SERVICE);
		when(incident.getEnvironment()).thenReturn(Environment.PROD);
		when(incident.getErrorLogs()).thenReturn("SocketTimeoutException");
		when(incident.getStatus()).thenReturn(IncidentStatus.RESOLVED);
		when(incident.getCreatedBy()).thenReturn(createdBy);
		when(incident.getAssignedDeveloper()).thenReturn(assignedDeveloper);
		when(incident.getAssignedAt()).thenReturn(Instant.parse("2026-06-21T10:40:00Z"));
		when(incident.getFinalCategory()).thenReturn(IncidentCategory.PERFORMANCE);
		when(incident.getFinalPriority()).thenReturn(IncidentPriority.CRITICAL);
		when(incident.getActualRootCause()).thenReturn("Connection pool exhaustion");
		when(incident.getActualResolution()).thenReturn("Increased the pool size");
		when(incident.getResolvedAt()).thenReturn(Instant.parse("2026-06-21T11:30:00Z"));
		when(incident.getCreatedAt()).thenReturn(Instant.parse("2026-06-21T10:30:00Z"));
		when(incident.getUpdatedAt()).thenReturn(Instant.parse("2026-06-21T11:30:00Z"));
		return incident;
	}

	private User user(Long id, String name, String username, String password, UserRole role) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getName()).thenReturn(name);
		when(user.getUsername()).thenReturn(username);
		when(user.getPassword()).thenReturn(password);
		when(user.getRole()).thenReturn(role);
		return user;
	}

	private AiAnalysis analysis() {
		AiAnalysis analysis = mock(AiAnalysis.class);
		when(analysis.getSuggestedCategory()).thenReturn(IncidentCategory.PERFORMANCE);
		when(analysis.getSuggestedPriority()).thenReturn(IncidentPriority.HIGH);
		when(analysis.getProbableRootCause()).thenReturn("Downstream connection pool exhaustion");
		when(analysis.getSuggestedResolution())
				.thenReturn("Increase the connection pool and monitor saturation");
		when(analysis.getModelName()).thenReturn("test-model");
		when(analysis.getGeneratedAt()).thenReturn(Instant.parse("2026-06-21T10:35:00Z"));
		return analysis;
	}

	private String token(String role) {
		Instant issuedAt = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("ai-incident-triage")
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plusSeconds(3600))
				.subject("details-user")
				.claim("userId", 1L)
				.claim("role", role)
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(),
				claims
		)).getTokenValue();
	}
}
