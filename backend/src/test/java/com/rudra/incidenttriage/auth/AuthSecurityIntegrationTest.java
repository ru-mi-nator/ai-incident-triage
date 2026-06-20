package com.rudra.incidenttriage.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.UserRole;
import com.rudra.incidenttriage.repository.AiAnalysisRepository;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import com.rudra.incidenttriage.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest(properties = {
		"security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
@Import(AuthSecurityIntegrationTest.TestApiController.class)
class AuthSecurityIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private IncidentRepository incidentRepository;

	@MockitoBean
	private AiAnalysisRepository aiAnalysisRepository;

	@BeforeEach
	void configureUsers() {
		User supportUser = user(
				1L,
				"Support Engineer One",
				"support1",
				"$2a$10$lZ.GrdfB1vnz.ZlycjDPfuI2vaVbNeFpWWTn0OLUgoZE5tCHzoRTC",
				UserRole.SUPPORT_ENGINEER
		);
		User developerUser = user(
				3L,
				"Developer One",
				"developer1",
				"$2a$10$Y7cY4giz4JEXJpMzQmUzS.89BSQAY7tV9z.SAFxIIEAssqN6psEw.",
				UserRole.DEVELOPER
		);

		when(userRepository.findByUsername("support1"))
				.thenReturn(Optional.of(supportUser));
		when(userRepository.findByUsername("developer1"))
				.thenReturn(Optional.of(developerUser));
		when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
	}

	@Test
	void supportUserLoginSucceeds() throws Exception {
		mockMvc.perform(login("support1", "Support@123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600))
				.andExpect(jsonPath("$.user.id").value(1))
				.andExpect(jsonPath("$.user.name").value("Support Engineer One"))
				.andExpect(jsonPath("$.user.username").value("support1"))
				.andExpect(jsonPath("$.user.role").value("SUPPORT_ENGINEER"))
				.andExpect(jsonPath("$.user.password").doesNotExist());
	}

	@Test
	void developerLoginSucceeds() throws Exception {
		mockMvc.perform(login("developer1", "Developer@123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.user.id").value(3))
				.andExpect(jsonPath("$.user.name").value("Developer One"))
				.andExpect(jsonPath("$.user.role").value("DEVELOPER"));
	}

	@Test
	void wrongPasswordReturnsInvalidCredentials() throws Exception {
		mockMvc.perform(login("support1", "wrong-password"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.message").value("Invalid username or password"))
				.andExpect(jsonPath("$.path").value("/api/auth/login"));
	}

	@Test
	void unknownUsernameReturnsSameCredentialErrorAsWrongPassword() throws Exception {
		JsonNode wrongPassword = errorBody(login("support1", "wrong-password"));
		JsonNode unknownUsername = errorBody(login("missing", "Support@123"));

		assertThat(unknownUsername.get("status")).isEqualTo(wrongPassword.get("status"));
		assertThat(unknownUsername.get("errorCode")).isEqualTo(wrongPassword.get("errorCode"));
		assertThat(unknownUsername.get("message")).isEqualTo(wrongPassword.get("message"));
		assertThat(unknownUsername.get("path")).isEqualTo(wrongPassword.get("path"));
	}

	@Test
	void protectedApiWithoutTokenReturnsJsonUnauthorized() throws Exception {
		mockMvc.perform(get("/api/test/protected"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("Authentication is required"))
				.andExpect(jsonPath("$.path").value("/api/test/protected"));
	}

	@Test
	void generatedTokenContainsExpectedClaimsAndOneHourExpiry() throws Exception {
		String token = accessToken(loginResult("support1", "Support@123"));
		Jwt jwt = jwtDecoder.decode(token);

		assertThat(jwt.getSubject()).isEqualTo("support1");
		assertThat(((Number) jwt.getClaim("userId")).longValue()).isEqualTo(1L);
		assertThat(jwt.getClaimAsString("role")).isEqualTo("SUPPORT_ENGINEER");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("ai-incident-triage");
		assertThat(jwt.getIssuedAt()).isNotNull();
		assertThat(jwt.getExpiresAt()).isNotNull();
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds()).isEqualTo(3600);
		assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
	}

	@Test
	void jwtRoleMapsToCorrectSpringAuthorityAndTokenIsAccepted() throws Exception {
		String token = accessToken(loginResult("developer1", "Developer@123"));

		mockMvc.perform(get("/api/test/authorities").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0]").value("ROLE_DEVELOPER"));
	}

	@Test
	void validTokenWithInsufficientPermissionReturnsJsonForbidden() throws Exception {
		String token = accessToken(loginResult("support1", "Support@123"));

		mockMvc.perform(get("/api/test/developer-only").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
				.andExpect(jsonPath("$.message")
						.value("You do not have permission to perform this action"))
				.andExpect(jsonPath("$.path").value("/api/test/developer-only"));
	}

	@Test
	void supportRoleMapsToCorrectSpringAuthority() throws Exception {
		String token = accessToken(loginResult("support1", "Support@123"));

		mockMvc.perform(get("/api/test/authorities").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0]").value("ROLE_SUPPORT_ENGINEER"));
	}

	@Test
	void blankUsernameReturnsStandardValidationResponse() throws Exception {
		mockMvc.perform(login(" ", "Support@123"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Request validation failed"))
				.andExpect(jsonPath("$.path").value("/api/auth/login"))
				.andExpect(jsonPath("$.fieldErrors.username").value("Username must not be blank"))
				.andExpect(jsonPath("$.fieldErrors.password").doesNotExist());
	}

	@Test
	void blankPasswordReturnsStandardValidationResponseWithoutPasswordValue() throws Exception {
		MvcResult result = mockMvc.perform(login("support1", "   "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Request validation failed"))
				.andExpect(jsonPath("$.path").value("/api/auth/login"))
				.andExpect(jsonPath("$.fieldErrors.password").value("Password must not be blank"))
				.andExpect(jsonPath("$.fieldErrors.username").doesNotExist())
				.andReturn();

		assertThat(result.getResponse().getContentAsString()).doesNotContain("\"password\":\"   \"");
		assertThat(result.getResponse().getContentAsString()).doesNotContain("rejectedValue");
	}

	@Test
	void bothBlankFieldsReturnBothFieldErrors() throws Exception {
		mockMvc.perform(login(" ", " "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.username").value("Username must not be blank"))
				.andExpect(jsonPath("$.fieldErrors.password").value("Password must not be blank"));
	}

	@Test
	void authenticatedUserErasesPasswordAfterAuthenticationAndNeverLogsIt() {
		String passwordHash = "$2a$10$exampleHashThatMustNotBeLogged";
		AuthenticatedUser principalForLogging = AuthenticatedUser.from(user(
				9L,
				"Safe User",
				"safe-user",
				passwordHash,
				UserRole.SUPPORT_ENGINEER
		));

		assertThat(principalForLogging.getPassword()).isEqualTo(passwordHash);
		assertThat(principalForLogging.toString()).doesNotContain(passwordHash);

		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated("support1", "Support@123")
		);
		AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();

		assertThat(authenticatedUser.getPassword()).isNull();
		assertThat(authenticatedUser.toString()).doesNotContain("password");
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
			String username,
			String password
	) throws Exception {
		return post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"username", username,
						"password", password
				)));
	}

	private MvcResult loginResult(String username, String password) throws Exception {
		return mockMvc.perform(login(username, password))
				.andExpect(status().isOk())
				.andReturn();
	}

	private String accessToken(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.get("accessToken")
				.asText();
	}

	private JsonNode errorBody(
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
	) throws Exception {
		MvcResult result = mockMvc.perform(request)
				.andExpect(status().isUnauthorized())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
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

	@RestController
	static class TestApiController {

		@GetMapping("/api/test/protected")
		Map<String, Boolean> protectedEndpoint() {
			return Map.of("authenticated", true);
		}

		@GetMapping("/api/test/authorities")
		Collection<String> authorities(Authentication authentication) {
			return authentication.getAuthorities().stream()
					.map(GrantedAuthority::getAuthority)
					.toList();
		}

		@GetMapping("/api/test/developer-only")
		@PreAuthorize("hasRole('DEVELOPER')")
		Map<String, Boolean> developerOnly() {
			return Map.of("allowed", true);
		}
	}
}
