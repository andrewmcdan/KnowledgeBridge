package com.capstone.knowledgebridge.knowledge;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "knowledge_item")
public class KnowledgeItem {
	/*
	 * Maps the java class values to a column in the table within @Table(name) This will need to be updated as the
	 * schema for 'knowledge_item' changes.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String type;

	@Column(name = "owner_id", nullable = false)
	private Long ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private KnowledgeItemStatus status;

	@Column(name = "character_count", nullable = false)
	private int characterCount;

	// Kept so retry can resubmit the same content and GET /{id} can show full text later - we used to
	// only store character_count.
	@Column(name = "body_text", nullable = false)
	private String bodyText;

	// Per-item version gbrain needs on every write (GbrainDocument.revision). Not the global
	// knowledge_revision in system_state - that one's a single counter for cache invalidation.
	@Column(nullable = false)
	private long revision;

	@Column(name = "external_engine_id")
	private String externalEngineId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected KnowledgeItem() {
		// JPA
	}

	/*--- Constructor ---*/
	public KnowledgeItem(String title, String type, Long ownerId, String bodyText) {
		this.title = title;
		this.type = type;
		this.ownerId = ownerId;
		this.bodyText = bodyText;
		this.characterCount = bodyText.length();
		this.status = KnowledgeItemStatus.PENDING;
		this.revision = 1;
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	/*--- Transition Methods ---*/
	public void markProcessing() {
		this.status = KnowledgeItemStatus.PROCESSING;
		this.updatedAt = Instant.now();
	}

	public void markCompleted(String externalEngineId) {
		this.status = KnowledgeItemStatus.COMPLETED;
		this.externalEngineId = externalEngineId;
		this.updatedAt = Instant.now();
	}

	public void markFailed() {
		this.status = KnowledgeItemStatus.FAILED;
		this.updatedAt = Instant.now();
	}

	/*--- GETTERS ---*/
	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getType() {
		return type;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public KnowledgeItemStatus getStatus() {
		return status;
	}

	public int getCharacterCount() {
		return characterCount;
	}

	public String getBodyText() {
		return bodyText;
	}

	public long getRevision() {
		return revision;
	}

	public String getExternalEngineId() {
		return externalEngineId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
