package com.rudra.incidenttriage.domain.entity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.rudra.incidenttriage.domain.enums.IncidentCategory;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
import com.rudra.incidenttriage.domain.enums.IncidentStatus;
import org.junit.jupiter.api.Test;

class IncidentFactoryTest {

	@Test
	void openCreatesAnOpenIncidentWithCreatorAndNoAssignmentOrReviewState() {
		User creator = new User();

		Incident incident = Incident.open(
				"Login API returning 500",
				"Users are unable to log in.",
				ApplicationName.AUTH_SERVICE,
				Environment.PROD,
				"NullPointerException",
				creator
		);

		assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
		assertThat(incident.getCreatedBy()).isSameAs(creator);
		assertThat(incident.getAssignedDeveloper()).isNull();
		assertThat(incident.getAssignedAt()).isNull();
		assertThat(incident.getResolvedAt()).isNull();
		assertThat(incident.getFinalCategory()).isNull();
		assertThat(incident.getFinalPriority()).isNull();
		assertThat(incident.getActualRootCause()).isNull();
		assertThat(incident.getActualResolution()).isNull();
		assertThat(incident.getCreatedAt()).isNull();
		assertThat(incident.getUpdatedAt()).isNull();
	}

	@Test
	void openRejectsMissingCreator() {
		assertThatNullPointerException().isThrownBy(() -> Incident.open(
				"Login API returning 500",
				"Users are unable to log in.",
				ApplicationName.AUTH_SERVICE,
				Environment.PROD,
				null,
				null
		));
	}

	@Test
	void assignToChangesDeveloperStatusAndTimestampTogether() {
		User creator = new User();
		User developer = new User();
		Instant assignedAt = Instant.parse("2026-06-21T12:00:00Z");
		Incident incident = Incident.open(
				"Login API returning 500",
				"Users are unable to log in.",
				ApplicationName.AUTH_SERVICE,
				Environment.PROD,
				null,
				creator
		);

		incident.assignTo(developer, assignedAt);

		assertThat(incident.getAssignedDeveloper()).isSameAs(developer);
		assertThat(incident.getAssignedAt()).isEqualTo(assignedAt);
		assertThat(incident.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
	}

	@Test
	void resolveSetsFinalDecisionAndLifecycleStateAtomically() {
		Incident incident = assignedIncident();
		Instant resolvedAt = Instant.parse("2026-06-21T13:00:00Z");

		incident.resolve(
				IncidentCategory.DATABASE,
				IncidentPriority.HIGH,
				"  Connection pool exhaustion  ",
				"  Closed leaked connections  ",
				resolvedAt
		);

		assertThat(incident.getFinalCategory()).isEqualTo(IncidentCategory.DATABASE);
		assertThat(incident.getFinalPriority()).isEqualTo(IncidentPriority.HIGH);
		assertThat(incident.getActualRootCause()).isEqualTo("Connection pool exhaustion");
		assertThat(incident.getActualResolution()).isEqualTo("Closed leaked connections");
		assertThat(incident.getResolvedAt()).isEqualTo(resolvedAt);
		assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
	}

	@Test
	void resolveRejectsInvalidStateMissingAssignmentAndInvalidValues() {
		Incident openIncident = openIncident();
		assertThatIllegalStateException().isThrownBy(() -> resolve(openIncident, "cause", "resolution"));

		Incident unassigned = openIncident();
		unassigned.setStatus(IncidentStatus.IN_PROGRESS);
		assertThatIllegalStateException().isThrownBy(() -> resolve(unassigned, "cause", "resolution"));

		Incident assigned = assignedIncident();
		assertThatIllegalArgumentException().isThrownBy(() -> resolve(assigned, " ", "resolution"));
		assertThatIllegalArgumentException().isThrownBy(() ->
				resolve(assignedIncident(), "x".repeat(2001), "resolution")
		);
		assertThatIllegalArgumentException().isThrownBy(() ->
				resolve(assignedIncident(), "cause", "x".repeat(3001))
		);
		assertThatNullPointerException().isThrownBy(() -> assignedIncident().resolve(
				null,
				IncidentPriority.HIGH,
				"cause",
				"resolution",
				Instant.now()
		));
	}

	@Test
	void resolvedIncidentCannotBeResolvedAgain() {
		Incident incident = assignedIncident();
		resolve(incident, "cause", "resolution");

		assertThatIllegalStateException().isThrownBy(() ->
				resolve(incident, "different cause", "different resolution")
		);
	}

	private Incident assignedIncident() {
		Incident incident = openIncident();
		incident.assignTo(new User(), Instant.parse("2026-06-21T12:00:00Z"));
		return incident;
	}

	private Incident openIncident() {
		return Incident.open(
				"Login API returning 500",
				"Users are unable to log in.",
				ApplicationName.AUTH_SERVICE,
				Environment.PROD,
				null,
				new User()
		);
	}

	private void resolve(Incident incident, String rootCause, String resolution) {
		incident.resolve(
				IncidentCategory.DATABASE,
				IncidentPriority.HIGH,
				rootCause,
				resolution,
				Instant.parse("2026-06-21T13:00:00Z")
		);
	}
}
