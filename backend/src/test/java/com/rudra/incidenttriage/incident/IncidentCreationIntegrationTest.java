package com.rudra.incidenttriage.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.UserRole;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class IncidentCreationIntegrationTest {

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

	private User supportUser;

	@BeforeEach
	void configurePersistence() {
		supportUser = user(1L, "Support Engineer One", "support1", UserRole.SUPPORT_ENGINEER);
		when(userRepository.findById(1L)).thenReturn(Optional.of(supportUser));
		when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
			Incident incident = invocation.getArgument(0);
			incident.setId(42L);
			incident.setCreatedAt(Instant.parse("2026-06-20T12:00:00Z"));
			incident.setUpdatedAt(Instant.parse("2026-06-20T12:00:00Z"));
			return incident;
		});
	}

	@Test
	void supportEngineerCreatesOpenUnassignedIncidentUsingJwtCreator() throws Exception {
		Map<String, Object> request = Map.of(
				"title", "Login API returning 500",
				"description", "Users are unable to log in after deployment.",
				"applicationName", "AUTH_SERVICE",
				"environment", "PROD",
				"errorLogs", "NullPointerException",
				"createdById", 999,
				"assignedDeveloperId", 3
		);

		mockMvc.perform(post("/api/incidents")
						.header("Authorization", "Bearer " + token(1L, "support1", "SUPPORT_ENGINEER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.displayId").value("INC-0042"))
				.andExpect(jsonPath("$.title").value("Login API returning 500"))
				.andExpect(jsonPath("$.description").value("Users are unable to log in after deployment."))
				.andExpect(jsonPath("$.applicationName").value("AUTH_SERVICE"))
				.andExpect(jsonPath("$.environment").value("PROD"))
				.andExpect(jsonPath("$.errorLogs").value("NullPointerException"))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.creator.id").value(1))
				.andExpect(jsonPath("$.creator.name").value("Support Engineer One"))
				.andExpect(jsonPath("$.creator.username").value("support1"))
				.andExpect(jsonPath("$.creator.role").value("SUPPORT_ENGINEER"))
				.andExpect(jsonPath("$.assignedDeveloper").value((Object) null))
				.andExpect(jsonPath("$.createdAt").value("2026-06-20T12:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-06-20T12:00:00Z"));

		verify(userRepository).findById(1L);
		ArgumentCaptor<Incident> incidentCaptor = ArgumentCaptor.forClass(Incident.class);
		verify(incidentRepository).save(incidentCaptor.capture());
		Incident savedIncident = incidentCaptor.getValue();
		assertThat(savedIncident.getCreatedBy()).isSameAs(supportUser);
		assertThat(savedIncident.getAssignedDeveloper()).isNull();
	}

	@Test
	void developerReceivesJsonAccessDenied() throws Exception {
		mockMvc.perform(post("/api/incidents")
						.header("Authorization", "Bearer " + token(3L, "developer1", "DEVELOPER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
				.andExpect(jsonPath("$.message")
						.value("You do not have permission to perform this action"))
				.andExpect(jsonPath("$.path").value("/api/incidents"));

		verifyNoInteractions(incidentRepository);
	}

	@Test
	void missingTokenReceivesJsonUnauthorized() throws Exception {
		mockMvc.perform(post("/api/incidents")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("Authentication is required"))
				.andExpect(jsonPath("$.path").value("/api/incidents"));
	}

	@Test
	void invalidFieldsReturnStandardValidationResponse() throws Exception {
		Map<String, Object> request = Map.of(
				"title", " ",
				"description", " ",
				"errorLogs", "x".repeat(10001)
		);

		mockMvc.perform(post("/api/incidents")
						.header("Authorization", "Bearer " + token(1L, "support1", "SUPPORT_ENGINEER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Request validation failed"))
				.andExpect(jsonPath("$.path").value("/api/incidents"))
				.andExpect(jsonPath("$.fieldErrors.title").value("Title must not be blank"))
				.andExpect(jsonPath("$.fieldErrors.description").value("Description must not be blank"))
				.andExpect(jsonPath("$.fieldErrors.applicationName")
						.value("Application name must not be null"))
				.andExpect(jsonPath("$.fieldErrors.environment").value("Environment must not be null"))
				.andExpect(jsonPath("$.fieldErrors.errorLogs")
						.value("Error logs must not exceed 10000 characters"));

		verifyNoInteractions(incidentRepository);
	}

	@Test
	void authenticatedDatabaseUserNoLongerExistsReturnsJsonUnauthorized() throws Exception {
		when(userRepository.findById(99L)).thenReturn(Optional.empty());

		mockMvc.perform(post("/api/incidents")
						.header("Authorization", "Bearer " + token(99L, "deleted", "SUPPORT_ENGINEER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("Authentication is required"))
				.andExpect(jsonPath("$.path").value("/api/incidents"));
	}

	@Test
	void databaseRoleMismatchReturnsJsonAccessDenied() throws Exception {
		User developer = user(3L, "Developer One", "developer1", UserRole.DEVELOPER);
		when(userRepository.findById(3L)).thenReturn(Optional.of(developer));

		mockMvc.perform(post("/api/incidents")
						.header("Authorization", "Bearer " + token(3L, "developer1", "SUPPORT_ENGINEER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
				.andExpect(jsonPath("$.message")
						.value("You do not have permission to perform this action"))
				.andExpect(jsonPath("$.path").value("/api/incidents"));

		verifyNoInteractions(incidentRepository);
	}

	@Test
	void invalidApplicationNameReturnsStandardValidationResponseWithoutRejectedValue() throws Exception {
		String rejectedValue = "NOT_A_SERVICE";
		String request = objectMapper.writeValueAsString(Map.of(
				"title", "Login API returning 500",
				"description", "Users are unable to log in after deployment.",
				"applicationName", rejectedValue,
				"environment", "PROD"
		));

		String response = mockMvc.perform(post("/api/incidents")
						.header("Authorization", "Bearer " + token(1L, "support1", "SUPPORT_ENGINEER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Request validation failed"))
				.andExpect(jsonPath("$.path").value("/api/incidents"))
				.andExpect(jsonPath("$.fieldErrors.applicationName").value("Invalid application name"))
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(response).doesNotContain(rejectedValue);
		verifyNoInteractions(incidentRepository);
	}

	@Test
	void invalidEnvironmentReturnsStandardValidationResponseWithoutRejectedValue() throws Exception {
		String rejectedValue = "STAGING";
		String request = objectMapper.writeValueAsString(Map.of(
				"title", "Login API returning 500",
				"description", "Users are unable to log in after deployment.",
				"applicationName", "AUTH_SERVICE",
				"environment", rejectedValue
		));

		String response = mockMvc.perform(post("/api/incidents")
						.header("Authorization", "Bearer " + token(1L, "support1", "SUPPORT_ENGINEER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Request validation failed"))
				.andExpect(jsonPath("$.path").value("/api/incidents"))
				.andExpect(jsonPath("$.fieldErrors.environment").value("Invalid environment"))
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(response).doesNotContain(rejectedValue);
		verifyNoInteractions(incidentRepository);
	}

	@Test
	void malformedJsonReturnsStandardInvalidRequestBodyResponse() throws Exception {
		mockMvc.perform(post("/api/incidents")
						.header("Authorization", "Bearer " + token(1L, "support1", "SUPPORT_ENGINEER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Broken JSON\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"))
				.andExpect(jsonPath("$.message").value("Request body is invalid"))
				.andExpect(jsonPath("$.path").value("/api/incidents"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());

		verifyNoInteractions(incidentRepository);
	}

	private String validRequest() throws Exception {
		return objectMapper.writeValueAsString(Map.of(
				"title", "Login API returning 500",
				"description", "Users are unable to log in after deployment.",
				"applicationName", "AUTH_SERVICE",
				"environment", "PROD",
				"errorLogs", "NullPointerException"
		));
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

	private User user(Long id, String name, String username, UserRole role) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getName()).thenReturn(name);
		when(user.getUsername()).thenReturn(username);
		when(user.getRole()).thenReturn(role);
		return user;
	}
}
