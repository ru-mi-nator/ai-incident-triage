package com.rudra.incidenttriage.incident;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
import com.rudra.incidenttriage.domain.enums.UserRole;
import com.rudra.incidenttriage.incident.dto.CreateIncidentRequest;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import org.junit.jupiter.api.Test;

class IncidentServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final IncidentRepository incidentRepository = mock(IncidentRepository.class);
	private final IncidentService incidentService = new IncidentService(userRepository, incidentRepository);

	@Test
	void roleCheckDefendsAgainstDeveloperEvenWhenServiceIsCalledDirectly() {
		User developer = mock(User.class);
		when(developer.getRole()).thenReturn(UserRole.DEVELOPER);
		when(userRepository.findById(3L)).thenReturn(Optional.of(developer));

		assertThatThrownBy(() -> incidentService.createIncident(3L, validRequest()))
				.isInstanceOf(IncidentCreationAccessDeniedException.class);

		verifyNoInteractions(incidentRepository);
	}

	@Test
	void missingAuthenticatedDatabaseUserFailsSafely() {
		when(userRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> incidentService.createIncident(99L, validRequest()))
				.isInstanceOf(AuthenticatedUserNotFoundException.class);

		verifyNoInteractions(incidentRepository);
	}

	private CreateIncidentRequest validRequest() {
		return new CreateIncidentRequest(
				"Login API returning 500",
				"Users are unable to log in.",
				ApplicationName.AUTH_SERVICE,
				Environment.PROD,
				null
		);
	}
}
