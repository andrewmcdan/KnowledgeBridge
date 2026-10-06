package com.capstone.knowledgebridge.ingestion;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ingestion_attempt")
public class IngestionAttempt {
	/*
	 * Maps the java class values to a column in the table within @Table(name) This will need to be updated as the
	 * schema for 'ingestion_attempt' changes.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "knowledge_item_id", nullable = false)
	private Long knowledgeItemId;

	@Column(name = "attempt_number", nullable = false)
	private int attemptNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IngestionAttemptStatus status;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "completed_at", nullable = false)
	private Instant completedAt;

	@Column(name = "error_code")
	private String errorCode;

	@Column(name = "error_message")
	private String errorMessage;

	protected IngestionAttempt() {
		// JPA
	}

	/*--- Constructor ---*/
	private IngestionAttempt(
			Long knowledgeItemId,
			int attemptNumber,
			IngestionAttemptStatus status,
			Instant startedAt,
			Instant completedAt,
			String errorCode,
			String errorMessage) {
		// assign every field
		this.knowledgeItemId = knowledgeItemId;
		this.attemptNumber = attemptNumber;
		this.status = status;
		this.startedAt = startedAt;
		this.completedAt = completedAt;
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
	}

	/*--- Attempt Creating Methods ---*/
	public static IngestionAttempt succeeded(Long knowledgeItemId, int attemptNumber, Instant startedAt) {
		return new IngestionAttempt(knowledgeItemId, attemptNumber, IngestionAttemptStatus.COMPLETED, startedAt,
				Instant.now(), null, null);
	}

	public static IngestionAttempt failed(Long knowledgeItemId, int attemptNumber, Instant startedAt, String errorCode,
			String errorMessage) {
		return new IngestionAttempt(knowledgeItemId, attemptNumber, IngestionAttemptStatus.FAILED, startedAt,
				Instant.now(), errorCode, errorMessage);
	}

	/*---  GETTERS ---*/
	public Long getKnowledgeItemId() {
		return knowledgeItemId;
	}

	public int getAttemptNumber() {
		return attemptNumber;
	}

	public IngestionAttemptStatus getStatus() {
		return status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
