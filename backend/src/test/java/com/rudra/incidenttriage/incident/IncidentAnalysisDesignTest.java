package com.rudra.incidenttriage.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import com.rudra.incidenttriage.domain.entity.AiAnalysis;
import com.rudra.incidenttriage.domain.entity.Incident;
import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
import com.rudra.incidenttriage.domain.enums.IncidentCategory;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import com.rudra.incidenttriage.domain.enums.UserRole;
import com.rudra.incidenttriage.incident.ai.IncidentTriageAiClient.IncidentTriageAiResult;
import com.rudra.incidenttriage.repository.AiAnalysisRepository;
import com.rudra.incidenttriage.repository.IncidentRepository;
import com.rudra.incidenttriage.repository.UserRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

class IncidentAnalysisDesignTest {

	@Test
	void externalAiCallOrchestrationIsNotTransactionalButPersistenceIs() throws Exception {
		Method analyze = IncidentAnalysisService.class.getMethod("analyze", long.class, long.class);
		Method persist = IncidentAnalysisPersistenceService.class.getMethod(
				"persist",
				long.class,
				long.class,
				IncidentTriageAiResult.class
		);

		assertThat(analyze.getAnnotation(Transactional.class)).isNull();
		assertThat(persist.getAnnotation(Transactional.class)).isNotNull();
	}

	@Test
	void persistenceReloadUsesDirectPessimisticWriteLock() throws Exception {
		Method lockedLookup = IncidentRepository.class.getMethod(
				"findByIdForAnalysis",
				Long.class
		);

		assertThat(lockedLookup.getAnnotation(Lock.class).value())
				.isEqualTo(LockModeType.PESSIMISTIC_WRITE);
		assertThat(lockedLookup.getAnnotation(EntityGraph.class)).isNull();
	}

	@Test
	void unrelatedIntegrityFailureIsNotMisreportedAsDuplicateAnalysis() {
		UserRepository userRepository = mock(UserRepository.class);
		IncidentRepository incidentRepository = mock(IncidentRepository.class);
		AiAnalysisRepository aiAnalysisRepository = mock(AiAnalysisRepository.class);
		IncidentAnalysisPersistenceService service = new IncidentAnalysisPersistenceService(
				userRepository,
				incidentRepository,
				aiAnalysisRepository
		);
		ReflectionTestUtils.setField(service, "modelName", "test-model");

		User creator = mock(User.class);
		when(creator.getId()).thenReturn(1L);
		when(creator.getRole()).thenReturn(UserRole.SUPPORT_ENGINEER);
		Incident incident = Incident.open(
				"Payment API timeout",
				"Payments fail after thirty seconds.",
				ApplicationName.PAYMENT_SERVICE,
				Environment.PROD,
				null,
				creator
		);
		incident.setId(42L);
		incident.setCreatedAt(Instant.parse("2026-06-21T10:30:00Z"));
		incident.setUpdatedAt(Instant.parse("2026-06-21T10:30:00Z"));

		when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
		when(incidentRepository.findByIdForAnalysis(42L)).thenReturn(Optional.of(incident));
		when(aiAnalysisRepository.existsByIncidentId(42L)).thenReturn(false);
		DataIntegrityViolationException failure = new DataIntegrityViolationException(
				"unrelated integrity failure"
		);
		when(aiAnalysisRepository.saveAndFlush(any(AiAnalysis.class))).thenThrow(failure);

		IncidentTriageAiResult result = new IncidentTriageAiResult(
				IncidentCategory.PERFORMANCE,
				IncidentPriority.HIGH,
				"Connection pool exhaustion",
				"Increase connection pool capacity"
		);

		assertThatThrownBy(() -> service.persist(42L, 1L, result)).isSameAs(failure);
	}
}
