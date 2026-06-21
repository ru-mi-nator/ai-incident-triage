package com.rudra.incidenttriage.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import com.rudra.incidenttriage.domain.enums.IncidentStatus;
import com.rudra.incidenttriage.domain.enums.UserRole;
import com.rudra.incidenttriage.incident.dto.CreateIncidentRequest;
import com.rudra.incidenttriage.incident.dto.IncidentPageResponse;
import com.rudra.incidenttriage.repository.AiAnalysisRepository;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class IncidentServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final IncidentRepository incidentRepository = mock(IncidentRepository.class);
	private final AiAnalysisRepository aiAnalysisRepository = mock(AiAnalysisRepository.class);
	private final IncidentService incidentService = new IncidentService(
			userRepository,
			incidentRepository,
			aiAnalysisRepository
	);

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

	@Test
	void finalPriorityOverridesAiPriorityAndAiPrioritiesAreFetchedInOneBatch() {
		Incident finalPriorityIncident = incident(41L, IncidentPriority.CRITICAL, null);
		Incident aiPriorityIncident = incident(42L, null, null);
		PageImpl<Incident> page = new PageImpl<>(
				List.of(finalPriorityIncident, aiPriorityIncident),
				PageRequest.of(0, 10),
				2
		);
		when(incidentRepository.findAll(any(PageRequest.class))).thenReturn(page);
		AiAnalysisRepository.IncidentPriorityProjection firstPriority = priority(41L, IncidentPriority.LOW);
		AiAnalysisRepository.IncidentPriorityProjection secondPriority = priority(42L, IncidentPriority.HIGH);
		when(aiAnalysisRepository.findPrioritiesByIncidentIds(List.of(41L, 42L)))
				.thenReturn(List.of(firstPriority, secondPriority));

		IncidentPageResponse response = incidentService.listIncidents("0", "10", "createdAt,desc");

		assertThat(response.content().get(0).priority()).isEqualTo(IncidentPriority.CRITICAL);
		assertThat(response.content().get(1).priority()).isEqualTo(IncidentPriority.HIGH);
		verify(aiAnalysisRepository).findPrioritiesByIncidentIds(List.of(41L, 42L));
	}

	@Test
	void priorityIsNullWhenNoFinalOrAiPriorityExists() {
		Incident incident = incident(43L, null, null);
		when(incidentRepository.findAll(any(PageRequest.class)))
				.thenReturn(new PageImpl<>(List.of(incident), PageRequest.of(0, 10), 1));
		when(aiAnalysisRepository.findPrioritiesByIncidentIds(List.of(43L))).thenReturn(List.of());

		IncidentPageResponse response = incidentService.listIncidents("0", "10", "createdAt,desc");

		assertThat(response.content().get(0).priority()).isNull();
	}

	@Test
	void assignedDeveloperIsSafelyMappedAndDisplayIdWorksAbove9999() {
		User developer = mock(User.class);
		when(developer.getId()).thenReturn(3L);
		when(developer.getName()).thenReturn("Developer One");
		when(developer.getUsername()).thenReturn("developer1");
		when(developer.getRole()).thenReturn(UserRole.DEVELOPER);
		Incident incident = incident(10000L, null, developer);
		when(incidentRepository.findAll(any(PageRequest.class)))
				.thenReturn(new PageImpl<>(List.of(incident), PageRequest.of(0, 10), 1));
		when(aiAnalysisRepository.findPrioritiesByIncidentIds(List.of(10000L))).thenReturn(List.of());

		IncidentPageResponse response = incidentService.listIncidents("0", "10", "createdAt,desc");

		assertThat(response.content().get(0).displayId()).isEqualTo("INC-10000");
		assertThat(response.content().get(0).assignedDeveloper())
				.extracting("id", "name", "username", "role")
				.containsExactly(3L, "Developer One", "developer1", UserRole.DEVELOPER);
	}

	@Test
	void aiQueryIsSkippedForAnEmptyPage() {
		when(incidentRepository.findAll(any(PageRequest.class)))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 0));

		IncidentPageResponse response = incidentService.listIncidents("2", "10", "createdAt,desc");

		assertThat(response.content()).isEmpty();
		verify(aiAnalysisRepository, never()).findPrioritiesByIncidentIds(any());
	}

	@Test
	void missingIncidentDetailsThrowsScopedExceptionWithoutAiLookup() {
		when(incidentRepository.findDetailsById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> incidentService.getIncidentDetails(999L))
				.isInstanceOf(IncidentNotFoundException.class);

		verify(aiAnalysisRepository, never()).findByIncidentId(any());
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

	private Incident incident(Long id, IncidentPriority finalPriority, User assignedDeveloper) {
		Incident incident = mock(Incident.class);
		when(incident.getId()).thenReturn(id);
		when(incident.getTitle()).thenReturn("Incident " + id);
		when(incident.getApplicationName()).thenReturn(ApplicationName.PAYMENT_SERVICE);
		when(incident.getEnvironment()).thenReturn(Environment.PROD);
		when(incident.getStatus()).thenReturn(IncidentStatus.OPEN);
		when(incident.getFinalPriority()).thenReturn(finalPriority);
		when(incident.getAssignedDeveloper()).thenReturn(assignedDeveloper);
		when(incident.getCreatedAt()).thenReturn(Instant.parse("2026-06-21T10:30:00Z"));
		return incident;
	}

	private AiAnalysisRepository.IncidentPriorityProjection priority(
			Long incidentId,
			IncidentPriority priority
	) {
		AiAnalysisRepository.IncidentPriorityProjection projection =
				mock(AiAnalysisRepository.IncidentPriorityProjection.class);
		when(projection.getIncidentId()).thenReturn(incidentId);
		when(projection.getSuggestedPriority()).thenReturn(priority);
		return projection;
	}
}
