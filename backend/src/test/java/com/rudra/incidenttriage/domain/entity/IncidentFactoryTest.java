package com.rudra.incidenttriage.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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
}
