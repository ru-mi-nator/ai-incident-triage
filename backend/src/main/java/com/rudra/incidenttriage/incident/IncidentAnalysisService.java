package com.rudra.incidenttriage.incident;

import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.incident.ai.IncidentTriageAiClient;
import com.rudra.incidenttriage.incident.ai.IncidentTriageAiClient.IncidentTriageAiResult;
import com.rudra.incidenttriage.incident.ai.IncidentTriageAiClient.IncidentTriageInput;
import com.rudra.incidenttriage.incident.dto.IncidentDetailsResponse;
import com.rudra.incidenttriage.repository.AiAnalysisRepository;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IncidentAnalysisService {

	private final UserRepository userRepository;
	private final IncidentRepository incidentRepository;
	private final AiAnalysisRepository aiAnalysisRepository;
	private final IncidentTriageAiClient aiClient;
	private final IncidentAnalysisPersistenceService persistenceService;

	public IncidentDetailsResponse analyze(long incidentId, long authenticatedUserId) {
		User user = userRepository.findById(authenticatedUserId)
				.orElseThrow(AuthenticatedUserNotFoundException::new);
		Incident incident = incidentRepository.findDetailsById(incidentId)
				.orElseThrow(IncidentNotFoundException::new);

		IncidentAnalysisEligibility.validateAccess(incident, user);
		if (aiAnalysisRepository.existsByIncidentId(incidentId)) {
			throw new AiAnalysisAlreadyExistsException();
		}
		IncidentAnalysisEligibility.validateState(incident, user);

		IncidentTriageAiResult result;
		try {
			result = validateResult(aiClient.analyze(new IncidentTriageInput(
					incident.getTitle(),
					incident.getDescription(),
					incident.getApplicationName(),
					incident.getEnvironment(),
					incident.getErrorLogs()
			)));
		} catch (RuntimeException exception) {
			throw new AiServiceUnavailableException();
		}

		return persistenceService.persist(incidentId, authenticatedUserId, result);
	}

	private IncidentTriageAiResult validateResult(IncidentTriageAiResult result) {
		if (result == null
				|| result.suggestedCategory() == null
				|| result.suggestedPriority() == null) {
			throw new IllegalArgumentException("AI result is incomplete");
		}

		String rootCause = validatedText(result.probableRootCause(), 2000);
		String resolution = validatedText(result.suggestedResolution(), 3000);
		return new IncidentTriageAiResult(
				result.suggestedCategory(),
				result.suggestedPriority(),
				rootCause,
				resolution
		);
	}

	private String validatedText(String value, int maximumLength) {
		if (value == null) {
			throw new IllegalArgumentException("AI result is incomplete");
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()
				|| trimmed.length() > maximumLength
				|| trimmed.contains("```")) {
			throw new IllegalArgumentException("AI result is invalid");
		}
		return trimmed;
	}
}
