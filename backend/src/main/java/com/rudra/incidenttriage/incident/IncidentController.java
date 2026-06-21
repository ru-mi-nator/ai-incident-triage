package com.rudra.incidenttriage.incident;

import com.rudra.incidenttriage.incident.dto.CreateIncidentRequest;
import com.rudra.incidenttriage.incident.dto.IncidentDetailsResponse;
import com.rudra.incidenttriage.incident.dto.IncidentPageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

	private final IncidentService incidentService;

	@GetMapping
	@PreAuthorize("hasAnyRole('SUPPORT_ENGINEER', 'DEVELOPER')")
	public IncidentPageResponse listIncidents(
			@RequestParam(defaultValue = "0") String page,
			@RequestParam(defaultValue = "10") String size,
			@RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		return incidentService.listIncidents(page, size, sort);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('SUPPORT_ENGINEER', 'DEVELOPER')")
	public IncidentDetailsResponse getIncidentDetails(@PathVariable long id) {
		return incidentService.getIncidentDetails(id);
	}

	@PostMapping("/{id}/assign-to-me")
	@PreAuthorize("hasRole('DEVELOPER')")
	public IncidentDetailsResponse assignIncidentToMe(
			@PathVariable long id,
			@AuthenticationPrincipal Jwt jwt
	) {
		Number userId = jwt.getClaim("userId");
		return incidentService.assignIncidentToDeveloper(id, userId.longValue());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('SUPPORT_ENGINEER')")
	public IncidentDetailsResponse createIncident(
			@Valid @RequestBody CreateIncidentRequest request,
			@AuthenticationPrincipal Jwt jwt
	) {
		Number userId = jwt.getClaim("userId");
		return incidentService.createIncident(userId.longValue(), request);
	}
}
