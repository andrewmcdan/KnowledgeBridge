package com.capstone.knowledgebridge.jiraintegration;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The progress and result of syncing one Jira project. */
@Entity
@Table(name = "jira_sync_run")
public class JiraSyncRun {

	private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_key", nullable = false)
	private String projectKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private JiraSyncRunStatus status;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "created_count", nullable = false)
	private int createdCount;

	@Column(name = "updated_count", nullable = false)
	private int updatedCount;

	@Column(name = "unchanged_count", nullable = false)
	private int unchangedCount;

	@Column(name = "failed_count", nullable = false)
	private int failedCount;

	@Column(name = "error_code")
	private String errorCode;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "triggered_by", nullable = false)
	private Long triggeredBy;

	protected JiraSyncRun() {
		// JPA
	}

	public JiraSyncRun(String projectKey, Long triggeredBy) {
		this.projectKey = projectKey;
		this.triggeredBy = triggeredBy;
		this.status = JiraSyncRunStatus.RUNNING;
		this.startedAt = Instant.now();
	}

	void record(JiraSyncOutcome outcome) {
		switch (outcome) {
			case CREATED -> createdCount++;
			case UPDATED -> updatedCount++;
			case UNCHANGED -> unchangedCount++;
			default -> failedCount++;
		}
	}

	void complete() {
		status = JiraSyncRunStatus.COMPLETED;
		finishedAt = Instant.now();
	}

	void fail(String code, String message) {
		status = JiraSyncRunStatus.FAILED;
		finishedAt = Instant.now();
		errorCode = code;
		errorMessage = message.length() <= MAX_ERROR_MESSAGE_LENGTH
				? message
				: message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
	}

	public Long getId() {
		return id;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public JiraSyncRunStatus getStatus() {
		return status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public int getCreatedCount() {
		return createdCount;
	}

	public int getUpdatedCount() {
		return updatedCount;
	}

	public int getUnchangedCount() {
		return unchangedCount;
	}

	public int getFailedCount() {
		return failedCount;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Long getTriggeredBy() {
		return triggeredBy;
	}

}
