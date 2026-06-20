package com.rudra.incidenttriage.incident;

import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.UserRole;
import com.rudra.incidenttriage.incident.dto.CreateIncidentRequest;
import com.rudra.incidenttriage.incident.dto.IncidentDetailsResponse;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentService {

	private final UserRepository userRepository;
	private final IncidentRepository incidentRepository;

	@Transactional
	public IncidentDetailsResponse createIncident(long authenticatedUserId, CreateIncidentRequest request) {
		User creator = userRepository.findById(authenticatedUserId)
				.orElseThrow(AuthenticatedUserNotFoundException::new);

		if (creator.getRole() != UserRole.SUPPORT_ENGINEER) {
			throw new IncidentCreationAccessDeniedException();
		}

		Incident incident = Incident.open(
				request.title(),
				request.description(),
				request.applicationName(),
				request.environment(),
				request.errorLogs(),
				creator
		);

		return IncidentDetailsResponse.from(incidentRepository.save(incident));
	}
}
