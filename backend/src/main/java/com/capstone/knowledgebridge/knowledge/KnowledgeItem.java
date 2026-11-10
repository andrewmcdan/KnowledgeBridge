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

	@Column(name = "ai_title_requested", nullable = false)
	private boolean aiTitleRequested;

	@Column(nullable = false)
	private String type;

	@Column(name = "owner_id", nullable = false)
	private Long ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private KnowledgeItemStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "lifecycle_state", nullable = false)
	private KnowledgeLifecycle lifecycleState = KnowledgeLifecycle.ACTIVE;

	@Enumerated(EnumType.STRING)
	@Column(name = "trashed_from")
	private KnowledgeLifecycle trashedFrom;

	@Column(name = "soft_deleted_at")
	private Instant softDeletedAt;

	@Column(name = "character_count", nullable = false)
	private int characterCount;

	// Kept so retry can resubmit the same content and GET /{id} can show full text later - we used to
	// only store character_count.
	@Column(name = "body_text", nullable = false)
	private String bodyText;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_kind", nullable = false)
	private KnowledgeSourceKind sourceKind;

	@Enumerated(EnumType.STRING)
	@Column(name = "ingestion_stage", nullable = false)
	private IngestionStage ingestionStage;

	@Column(name = "original_filename")
	private String originalFilename;

	@Column(name = "source_content_type")
	private String sourceContentType;

	@Column(name = "source_sha256")
	private String sourceSha256;

	@Column(name = "source_path", length = 1024)
	private String sourcePath;

	@Column(name = "media_duration_ms")
	private Long mediaDurationMs;

	@Column(name = "transcription_model")
	private String transcriptionModel;

	@Column(name = "transcription_language")
	private String transcriptionLanguage;

	@Column(name = "transcription_segment_count")
	private Integer transcriptionSegmentCount;

	@Column(name = "transcribed_at")
	private Instant transcribedAt;

	// Per-item version gbrain needs on every write (GbrainDocument.revision). Not the global
	// knowledge_revision in system_state - that one's a single counter for cache invalidation.
	@Column(nullable = false)
	private long revision;

	@Column(name = "external_engine_id")
	private String externalEngineId;

	// Identifies the external record this item was synced from; all null for uploads and manual notes.
	@Column(name = "external_source", length = 32)
	private String externalSource;

	@Column(name = "external_key", length = 64)
	private String externalKey;

	@Column(name = "external_url", length = 512)
	private String externalUrl;

	@Column(name = "external_updated_at")
	private Instant externalUpdatedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "content_updated_at", nullable = false)
	private Instant contentUpdatedAt;

	protected KnowledgeItem() {
		// JPA
	}

	/*--- Constructor ---*/
	public KnowledgeItem(String title, String type, Long ownerId, String bodyText) {
		this(title, type, ownerId, bodyText, KnowledgeSourceKind.MANUAL);
	}

	public KnowledgeItem(String title, String type, Long ownerId, String bodyText, KnowledgeSourceKind sourceKind) {
		this.title = title;
		this.type = type;
		this.ownerId = ownerId;
		this.bodyText = bodyText;
		this.characterCount = bodyText.length();
		this.sourceKind = sourceKind;
		this.ingestionStage = IngestionStage.QUEUED;
		this.status = KnowledgeItemStatus.PENDING;
		this.revision = 1;
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		this.contentUpdatedAt = now;
	}

	public static KnowledgeItem video(String title, String type, Long ownerId, String originalFilename,
			String sourceContentType, String sourceSha256, String sourcePath) {
		KnowledgeItem item = new KnowledgeItem(title, type, ownerId, "", KnowledgeSourceKind.VIDEO);
		item.originalFilename = originalFilename;
		item.sourceContentType = sourceContentType;
		item.sourceSha256 = sourceSha256;
		item.sourcePath = sourcePath;
		return item;
	}

	/**
	 * A .docx or .pdf upload: bodyText is the converted Markdown, and the original file is not kept, so sourcePath is
	 * null.
	 */
	public static KnowledgeItem converted(String title, String type, Long ownerId, String bodyText,
			KnowledgeSourceKind sourceKind, String originalFilename, String sourceContentType, String sourceSha256) {
		KnowledgeItem item = new KnowledgeItem(title, type, ownerId, bodyText, sourceKind);
		item.originalFilename = originalFilename;
		item.sourceContentType = sourceContentType;
		item.sourceSha256 = sourceSha256;
		return item;
	}

	/** An item synced from an external system such as Jira, which remains the source of truth for its content. */
	public static KnowledgeItem external(String title, String type, Long ownerId, String bodyText,
			KnowledgeSourceKind sourceKind, String externalKey, String externalUrl, Instant externalUpdatedAt) {
		KnowledgeItem item = new KnowledgeItem(title, type, ownerId, bodyText, sourceKind);
		item.externalSource = sourceKind.name();
		item.externalKey = externalKey;
		item.externalUrl = externalUrl;
		item.externalUpdatedAt = externalUpdatedAt;
		return item;
	}

	/*--- Transition Methods ---*/
	/** Replaces content with the external system's latest version and advances the revision gbrain indexes. */
	public void applyExternalUpdate(String title, String type, String bodyText, String externalUrl,
			Instant externalUpdatedAt) {
		this.title = title;
		this.type = type;
		this.bodyText = bodyText;
		this.characterCount = bodyText.length();
		this.externalUrl = externalUrl;
		this.externalUpdatedAt = externalUpdatedAt;
		this.revision++;
		this.contentUpdatedAt = Instant.now();
		this.updatedAt = this.contentUpdatedAt;
	}

	public void requestAiTitle(boolean requested) {
		this.aiTitleRequested = requested;
	}

	public boolean isAiTitleRequested() {
		return aiTitleRequested;
	}

	public void applyGeneratedTitle(String generatedTitle) {
		this.title = generatedTitle;
		this.aiTitleRequested = false;
	}

	public void markProcessing() {
		markProcessing(IngestionStage.INDEXING);
	}

	public void markProcessing(IngestionStage stage) {
		this.status = KnowledgeItemStatus.PROCESSING;
		this.ingestionStage = stage;
		this.updatedAt = Instant.now();
	}

	public void markQueued() {
		this.status = KnowledgeItemStatus.PENDING;
		this.ingestionStage = IngestionStage.QUEUED;
		this.updatedAt = Instant.now();
	}

	public void storeTranscript(String transcript, long durationMs, String model, String language, int segmentCount) {
		this.bodyText = transcript;
		this.characterCount = transcript.length();
		this.mediaDurationMs = durationMs;
		this.transcriptionModel = model;
		this.transcriptionLanguage = language;
		this.transcriptionSegmentCount = segmentCount;
		this.transcribedAt = Instant.now();
		this.contentUpdatedAt = this.transcribedAt;
		this.ingestionStage = IngestionStage.INDEXING;
		this.updatedAt = Instant.now();
	}

	public void clearSourcePath() {
		this.sourcePath = null;
		this.updatedAt = Instant.now();
	}

	public void markCompleted(String externalEngineId) {
		this.status = KnowledgeItemStatus.COMPLETED;
		this.ingestionStage = IngestionStage.COMPLETE;
		this.externalEngineId = externalEngineId;
		this.updatedAt = Instant.now();
	}

	public void markFailed() {
		this.status = KnowledgeItemStatus.FAILED;
		this.ingestionStage = IngestionStage.FAILED;
		this.updatedAt = Instant.now();
	}

	/*--- GETTERS ---*/
	public KnowledgeLifecycle getLifecycleState() {
		return lifecycleState;
	}

	public KnowledgeLifecycle getTrashedFrom() {
		return trashedFrom;
	}

	public Instant getSoftDeletedAt() {
		return softDeletedAt;
	}

	public boolean isAvailable() {
		return lifecycleState == KnowledgeLifecycle.ACTIVE && status == KnowledgeItemStatus.COMPLETED;
	}

	public void changeLifecycle(KnowledgeLifecycle next) {
		trashedFrom = next == KnowledgeLifecycle.TRASH ? lifecycleState : null;
		softDeletedAt = next == KnowledgeLifecycle.TRASH ? Instant.now() : null;
		lifecycleState = next;
		updatedAt = Instant.now();
	}

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

	public KnowledgeSourceKind getSourceKind() {
		return sourceKind;
	}

	public IngestionStage getIngestionStage() {
		return ingestionStage;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getSourceContentType() {
		return sourceContentType;
	}

	public String getSourceSha256() {
		return sourceSha256;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public Long getMediaDurationMs() {
		return mediaDurationMs;
	}

	public String getTranscriptionModel() {
		return transcriptionModel;
	}

	public String getTranscriptionLanguage() {
		return transcriptionLanguage;
	}

	public Integer getTranscriptionSegmentCount() {
		return transcriptionSegmentCount;
	}

	public Instant getTranscribedAt() {
		return transcribedAt;
	}

	public long getRevision() {
		return revision;
	}

	public String getExternalEngineId() {
		return externalEngineId;
	}

	public String getExternalSource() {
		return externalSource;
	}

	public String getExternalKey() {
		return externalKey;
	}

	public String getExternalUrl() {
		return externalUrl;
	}

	public Instant getExternalUpdatedAt() {
		return externalUpdatedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getContentUpdatedAt() {
		return contentUpdatedAt;
	}
}
