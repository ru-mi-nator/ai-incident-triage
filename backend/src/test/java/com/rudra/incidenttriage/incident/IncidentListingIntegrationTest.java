package com.rudra.incidenttriage.incident;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
import com.rudra.incidenttriage.domain.enums.IncidentStatus;
import com.rudra.incidenttriage.repository.AiAnalysisRepository;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
class IncidentListingIntegrationTest {

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

	@BeforeEach
	void configurePage() {
		Incident incident = mock(Incident.class);
		when(incident.getId()).thenReturn(41L);
		when(incident.getTitle()).thenReturn("Payment API timeout");
		when(incident.getDescription()).thenReturn("Must not be serialized");
		when(incident.getErrorLogs()).thenReturn("Must not be serialized");
		when(incident.getApplicationName()).thenReturn(ApplicationName.PAYMENT_SERVICE);
		when(incident.getEnvironment()).thenReturn(Environment.PROD);
		when(incident.getStatus()).thenReturn(IncidentStatus.OPEN);
		when(incident.getCreatedAt()).thenReturn(Instant.parse("2026-06-21T10:30:00Z"));
		when(incidentRepository.findAll(any(Pageable.class))).thenAnswer(invocation -> {
			Pageable pageable = invocation.getArgument(0);
			return new PageImpl<>(List.of(incident), pageable, 1);
		});
		when(aiAnalysisRepository.findPrioritiesByIncidentIds(List.of(41L))).thenReturn(List.of());
	}

	@Test
	void supportEngineerCanListIncidents() throws Exception {
		performList("SUPPORT_ENGINEER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(41));
	}

	@Test
	void developerCanListIncidents() throws Exception {
		performList("DEVELOPER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(41));
	}

	@Test
	void missingTokenReturnsJsonUnauthorized() throws Exception {
		mockMvc.perform(get("/api/incidents"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("Authentication is required"))
				.andExpect(jsonPath("$.path").value("/api/incidents"));
	}

	@Test
	void defaultPaginationUsesNewestFirstSorting() throws Exception {
		performList("SUPPORT_ENGINEER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.first").value(true))
				.andExpect(jsonPath("$.last").value(true));

		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		org.mockito.Mockito.verify(incidentRepository).findAll(captor.capture());
		Pageable pageable = captor.getValue();
		org.assertj.core.api.Assertions.assertThat(pageable.getPageNumber()).isZero();
		org.assertj.core.api.Assertions.assertThat(pageable.getPageSize()).isEqualTo(10);
		org.assertj.core.api.Assertions.assertThat(pageable.getSort().getOrderFor("createdAt"))
				.isEqualTo(Sort.Order.desc("createdAt"));
	}

	@Test
	void customAllowedSortingIsApplied() throws Exception {
		mockMvc.perform(get("/api/incidents")
						.param("page", "2")
						.param("size", "25")
						.param("sort", "title,asc")
						.header("Authorization", "Bearer " + token("DEVELOPER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(2))
				.andExpect(jsonPath("$.size").value(25));

		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		org.mockito.Mockito.verify(incidentRepository).findAll(captor.capture());
		org.assertj.core.api.Assertions.assertThat(captor.getValue().getSort().getOrderFor("title"))
				.isEqualTo(Sort.Order.asc("title"));
	}

	@Test
	void invalidPageReturnsValidationFailed() throws Exception {
		assertInvalid("page", "-1", "page");
	}

	@Test
	void invalidSizeReturnsValidationFailed() throws Exception {
		assertInvalid("size", "51", "size");
	}

	@Test
	void invalidSortFieldReturnsValidationFailed() throws Exception {
		assertInvalid("sort", "assignedDeveloper.name,asc", "sort");
	}

	@Test
	void invalidSortDirectionReturnsValidationFailed() throws Exception {
		assertInvalid("sort", "createdAt,sideways", "sort");
	}

	@Test
	void summaryExcludesDescriptionAndErrorLogs() throws Exception {
		performList("SUPPORT_ENGINEER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].displayId").value("INC-0041"))
				.andExpect(jsonPath("$.content[0].priority").value((Object) null))
				.andExpect(jsonPath("$.content[0].assignedDeveloper").value((Object) null))
				.andExpect(jsonPath("$.content[0].createdAt").value("2026-06-21T10:30:00Z"))
				.andExpect(jsonPath("$.content[0].description").doesNotExist())
				.andExpect(jsonPath("$.content[0].errorLogs").doesNotExist());
	}

	private org.springframework.test.web.servlet.ResultActions performList(String role) throws Exception {
		return mockMvc.perform(get("/api/incidents")
				.header("Authorization", "Bearer " + token(role)));
	}

	private void assertInvalid(String parameter, String value, String field) throws Exception {
		mockMvc.perform(get("/api/incidents")
						.param(parameter, value)
						.header("Authorization", "Bearer " + token("SUPPORT_ENGINEER")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Request validation failed"))
				.andExpect(jsonPath("$.path").value("/api/incidents"))
				.andExpect(jsonPath("$.fieldErrors." + field).exists());

		verifyNoInteractions(incidentRepository, aiAnalysisRepository);
	}

	private String token(String role) {
		Instant issuedAt = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("ai-incident-triage")
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plusSeconds(3600))
				.subject("listing-user")
				.claim("userId", 1L)
				.claim("role", role)
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(),
				claims
		)).getTokenValue();
	}
}
