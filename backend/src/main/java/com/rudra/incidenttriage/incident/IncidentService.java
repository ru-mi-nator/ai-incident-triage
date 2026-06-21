package com.rudra.incidenttriage.incident;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import com.rudra.incidenttriage.domain.enums.UserRole;
import com.rudra.incidenttriage.incident.dto.CreateIncidentRequest;
import com.rudra.incidenttriage.incident.dto.IncidentDetailsResponse;
import com.rudra.incidenttriage.incident.dto.IncidentPageResponse;
import com.rudra.incidenttriage.incident.dto.IncidentSummaryResponse;
import com.rudra.incidenttriage.repository.AiAnalysisRepository;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentService {

	private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
			"id",
			"createdAt",
			"updatedAt",
			"title",
			"applicationName",
			"environment",
			"status"
	);

	private final UserRepository userRepository;
	private final IncidentRepository incidentRepository;
	private final AiAnalysisRepository aiAnalysisRepository;

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

	@Transactional(readOnly = true)
	public IncidentPageResponse listIncidents(String pageValue, String sizeValue, String sortValue) {
		ListRequest listRequest = validateListRequest(pageValue, sizeValue, sortValue);
		PageRequest pageable = PageRequest.of(
				listRequest.page(),
				listRequest.size(),
				Sort.by(listRequest.direction(), listRequest.sortField())
		);
		Page<Incident> incidents = incidentRepository.findAll(pageable);

		Map<Long, IncidentPriority> aiPriorities = incidents.isEmpty()
				? Map.of()
				: aiAnalysisRepository.findPrioritiesByIncidentIds(
						incidents.getContent().stream().map(Incident::getId).toList()
				).stream().collect(Collectors.toMap(
						AiAnalysisRepository.IncidentPriorityProjection::getIncidentId,
						AiAnalysisRepository.IncidentPriorityProjection::getSuggestedPriority
				));

		return IncidentPageResponse.from(
				incidents,
				incidents.getContent().stream()
						.map(incident -> IncidentSummaryResponse.from(
								incident,
								incident.getFinalPriority() != null
										? incident.getFinalPriority()
										: aiPriorities.get(incident.getId())
						))
						.toList()
		);
	}

	@Transactional(readOnly = true)
	public IncidentDetailsResponse getIncidentDetails(long incidentId) {
		Incident incident = incidentRepository.findDetailsById(incidentId)
				.orElseThrow(IncidentNotFoundException::new);

		return IncidentDetailsResponse.from(
				incident,
				aiAnalysisRepository.findByIncidentId(incidentId).orElse(null)
		);
	}

	private ListRequest validateListRequest(String pageValue, String sizeValue, String sortValue) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		Integer page = parseInteger(pageValue, "page", "Page must be at least 0", fieldErrors);
		Integer size = parseInteger(sizeValue, "size", "Size must be between 1 and 50", fieldErrors);

		if (page != null && page < 0) {
			fieldErrors.put("page", "Page must be at least 0");
		}
		if (size != null && (size < 1 || size > 50)) {
			fieldErrors.put("size", "Size must be between 1 and 50");
		}

		String[] sortParts = sortValue.split(",", -1);
		String sortField = sortParts.length > 0 ? sortParts[0] : "";
		String directionValue = sortParts.length > 1 ? sortParts[1] : "";
		if (sortParts.length != 2 || !ALLOWED_SORT_FIELDS.contains(sortField)) {
			fieldErrors.put("sort", "Sort field is invalid");
		} else if (!directionValue.equals("asc") && !directionValue.equals("desc")) {
			fieldErrors.put("sort", "Sort direction must be asc or desc");
		}

		if (!fieldErrors.isEmpty()) {
			throw new IncidentListValidationException(fieldErrors);
		}

		return new ListRequest(
				page,
				size,
				sortField,
				Sort.Direction.fromString(directionValue)
		);
	}

	private Integer parseInteger(
			String value,
			String field,
			String message,
			Map<String, String> fieldErrors
	) {
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException exception) {
			fieldErrors.put(field, message);
			return null;
		}
	}

	private record ListRequest(
			int page,
			int size,
			String sortField,
			Sort.Direction direction
	) {
	}
}
