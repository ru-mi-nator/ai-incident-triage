package com.rudra.incidenttriage.incident;

import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.IncidentStatus;
import com.rudra.incidenttriage.domain.enums.UserRole;

final class IncidentAnalysisEligibility {

	private IncidentAnalysisEligibility() {
	}

	static void validateAccess(Incident incident, User user) {
		if (user.getRole() == UserRole.SUPPORT_ENGINEER) {
			if (!incident.getCreatedBy().getId().equals(user.getId())) {
				throw new IncidentAnalysisAccessDeniedException();
			}
			return;
		}

		if (user.getRole() == UserRole.DEVELOPER) {
			if (incident.getAssignedDeveloper() != null
					&& !incident.getAssignedDeveloper().getId().equals(user.getId())) {
				throw new IncidentAnalysisAccessDeniedException();
			}
			return;
		}

		throw new IncidentAnalysisAccessDeniedException();
	}

	static void validateState(Incident incident, User user) {
		if (user.getRole() == UserRole.SUPPORT_ENGINEER) {
			if (incident.getStatus() != IncidentStatus.OPEN
					|| incident.getAssignedDeveloper() != null) {
				throw new IncidentNotAnalyzableException();
			}
			return;
		}

		if (user.getRole() == UserRole.DEVELOPER
				&& incident.getAssignedDeveloper() != null
				&& incident.getStatus() == IncidentStatus.IN_PROGRESS) {
			return;
		}

		throw new IncidentNotAnalyzableException();
	}
}
