package com.rudra.incidenttriage.incident;

import com.rudra.incidenttriage.domain.entity.AiAnalysis;
import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.incident.ai.IncidentTriageAiClient.IncidentTriageAiResult;
import com.rudra.incidenttriage.incident.dto.IncidentDetailsResponse;
import com.rudra.incidenttriage.repository.AiAnalysisRepository;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentAnalysisPersistenceService {

	private static final String UNIQUE_INCIDENT_ANALYSIS_CONSTRAINT = "uq_ai_analyses_incident_id";

	private final UserRepository userRepository;
	private final IncidentRepository incidentRepository;
	private final AiAnalysisRepository aiAnalysisRepository;

	@Value("${spring.ai.openai.chat.options.model}")
	private String modelName;

	@Transactional
	public IncidentDetailsResponse persist(
			long incidentId,
			long authenticatedUserId,
			IncidentTriageAiResult result
	) {
		User user = userRepository.findById(authenticatedUserId)
				.orElseThrow(AuthenticatedUserNotFoundException::new);
		Incident incident = incidentRepository.findByIdForAnalysis(incidentId)
				.orElseThrow(IncidentNotFoundException::new);

		IncidentAnalysisEligibility.validateAccess(incident, user);
		if (aiAnalysisRepository.existsByIncidentId(incidentId)) {
			throw new AiAnalysisAlreadyExistsException();
		}
		IncidentAnalysisEligibility.validateState(incident, user);

		AiAnalysis analysis = AiAnalysis.create(
				incident,
				result.suggestedCategory(),
				result.suggestedPriority(),
				result.probableRootCause(),
				result.suggestedResolution(),
				modelName
		);

		try {
			AiAnalysis savedAnalysis = aiAnalysisRepository.saveAndFlush(analysis);
			return IncidentDetailsResponse.from(incident, savedAnalysis);
		} catch (DataIntegrityViolationException exception) {
			if (isDuplicateAnalysisConstraint(exception)) {
				throw new AiAnalysisAlreadyExistsException();
			}
			throw exception;
		}
	}

	private boolean isDuplicateAnalysisConstraint(Throwable exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation
					&& UNIQUE_INCIDENT_ANALYSIS_CONSTRAINT.equals(
							constraintViolation.getConstraintName()
					)) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}
}
