package com.rudra.incidenttriage.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.rudra.incidenttriage.domain.enums.IncidentCategory;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import org.junit.jupiter.api.Test;

class AiAnalysisFactoryTest {

	@Test
	void createSetsStructuredFieldsAndJpaCallbackPopulatesGeneratedTimestamp() {
		Incident incident = new Incident();

		AiAnalysis analysis = AiAnalysis.create(
				incident,
				IncidentCategory.PERFORMANCE,
				IncidentPriority.HIGH,
				"Connection pool exhaustion",
				"Increase connection pool capacity",
				"gpt-4.1-mini"
		);

		assertThat(analysis.getIncident()).isSameAs(incident);
		assertThat(analysis.getSuggestedCategory()).isEqualTo(IncidentCategory.PERFORMANCE);
		assertThat(analysis.getSuggestedPriority()).isEqualTo(IncidentPriority.HIGH);
		assertThat(analysis.getProbableRootCause()).isEqualTo("Connection pool exhaustion");
		assertThat(analysis.getSuggestedResolution()).isEqualTo("Increase connection pool capacity");
		assertThat(analysis.getModelName()).isEqualTo("gpt-4.1-mini");
		assertThat(analysis.getGeneratedAt()).isNull();

		analysis.initializeGeneratedAt();

		assertThat(analysis.getGeneratedAt()).isNotNull();
	}
}
