package com.rudra.incidenttriage.domain.entity;

import java.time.Instant;

import com.rudra.incidenttriage.domain.enums.IncidentCategory;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
		name = "ai_analyses",
		uniqueConstraints = @UniqueConstraint(name = "uq_ai_analyses_incident_id", columnNames = "incident_id")
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiAnalysis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "incident_id", nullable = false)
	private Incident incident;

	@Enumerated(EnumType.STRING)
	@Column(name = "suggested_category", nullable = false, length = 50)
	private IncidentCategory suggestedCategory;

	@Enumerated(EnumType.STRING)
	@Column(name = "suggested_priority", nullable = false, length = 20)
	private IncidentPriority suggestedPriority;

	@Column(name = "probable_root_cause", nullable = false, columnDefinition = "TEXT")
	private String probableRootCause;

	@Column(name = "suggested_resolution", nullable = false, columnDefinition = "TEXT")
	private String suggestedResolution;

	@Column(name = "model_name", nullable = false, length = 100)
	private String modelName;

	@Column(name = "generated_at", nullable = false)
	private Instant generatedAt;

	@PrePersist
	void initializeGeneratedAt() {
		if (generatedAt == null) {
			generatedAt = Instant.now();
		}
	}
}
