package com.rudra.incidenttriage.domain.entity;

import java.time.Instant;
import java.util.Objects;

import com.rudra.incidenttriage.domain.enums.ApplicationName;
import com.rudra.incidenttriage.domain.enums.Environment;
import com.rudra.incidenttriage.domain.enums.IncidentCategory;
import com.rudra.incidenttriage.domain.enums.IncidentPriority;
import com.rudra.incidenttriage.domain.enums.IncidentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
		name = "incidents",
		indexes = {
				@Index(name = "idx_incidents_status", columnList = "status"),
				@Index(name = "idx_incidents_created_by_id", columnList = "created_by_id"),
				@Index(name = "idx_incidents_assigned_developer_id", columnList = "assigned_developer_id"),
				@Index(name = "idx_incidents_created_at", columnList = "created_at")
		}
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Incident {

	private static final int MAX_ROOT_CAUSE_LENGTH = 2000;
	private static final int MAX_RESOLUTION_LENGTH = 3000;

	public static Incident open(
			String title,
			String description,
			ApplicationName applicationName,
			Environment environment,
			String errorLogs,
			User createdBy
	) {
		Incident incident = new Incident();
		incident.title = Objects.requireNonNull(title, "title must not be null");
		incident.description = Objects.requireNonNull(description, "description must not be null");
		incident.applicationName = Objects.requireNonNull(applicationName, "applicationName must not be null");
		incident.environment = Objects.requireNonNull(environment, "environment must not be null");
		incident.errorLogs = errorLogs;
		incident.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
		incident.status = IncidentStatus.OPEN;
		return incident;
	}

	public void assignTo(User developer, Instant assignedAt) {
		this.assignedDeveloper = Objects.requireNonNull(developer, "developer must not be null");
		this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt must not be null");
		this.status = IncidentStatus.IN_PROGRESS;
	}

	public void resolve(
			IncidentCategory finalCategory,
			IncidentPriority finalPriority,
			String actualRootCause,
			String actualResolution,
			Instant resolvedAt
	) {
		if (status != IncidentStatus.IN_PROGRESS) {
			throw new IllegalStateException("incident must be in progress");
		}
		if (assignedDeveloper == null) {
			throw new IllegalStateException("incident must have an assigned developer");
		}

		IncidentCategory validatedCategory = Objects.requireNonNull(
				finalCategory,
				"finalCategory must not be null"
		);
		IncidentPriority validatedPriority = Objects.requireNonNull(
				finalPriority,
				"finalPriority must not be null"
		);
		String validatedRootCause = validatedResolutionText(
				actualRootCause,
				MAX_ROOT_CAUSE_LENGTH,
				"actualRootCause"
		);
		String validatedResolution = validatedResolutionText(
				actualResolution,
				MAX_RESOLUTION_LENGTH,
				"actualResolution"
		);
		Instant validatedResolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");

		this.finalCategory = validatedCategory;
		this.finalPriority = validatedPriority;
		this.actualRootCause = validatedRootCause;
		this.actualResolution = validatedResolution;
		this.resolvedAt = validatedResolvedAt;
		this.status = IncidentStatus.RESOLVED;
	}

	private String validatedResolutionText(String value, int maximumLength, String fieldName) {
		if (value == null) {
			throw new NullPointerException(fieldName + " must not be null");
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		if (trimmed.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " exceeds maximum length");
		}
		return trimmed;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "title", nullable = false, length = 150)
	private String title;

	@Column(name = "description", nullable = false, columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "application_name", nullable = false, length = 50)
	private ApplicationName applicationName;

	@Enumerated(EnumType.STRING)
	@Column(name = "environment", nullable = false, length = 20)
	private Environment environment;

	@Column(name = "error_logs", columnDefinition = "TEXT")
	private String errorLogs;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private IncidentStatus status;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by_id", nullable = false)
	private User createdBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_developer_id")
	private User assignedDeveloper;

	@Enumerated(EnumType.STRING)
	@Column(name = "final_category", length = 50)
	private IncidentCategory finalCategory;

	@Enumerated(EnumType.STRING)
	@Column(name = "final_priority", length = 20)
	private IncidentPriority finalPriority;

	@Column(name = "actual_root_cause", columnDefinition = "TEXT")
	private String actualRootCause;

	@Column(name = "actual_resolution", columnDefinition = "TEXT")
	private String actualResolution;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "assigned_at")
	private Instant assignedAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@PrePersist
	void initializeTimestamps() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
	}

	@PreUpdate
	void updateTimestamp() {
		updatedAt = Instant.now();
	}
}
