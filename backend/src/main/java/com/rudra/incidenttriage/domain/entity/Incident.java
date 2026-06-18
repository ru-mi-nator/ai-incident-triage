package com.rudra.incidenttriage.domain.entity;

import java.time.Instant;

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
