package com.capstone.knowledgebridge.knowledge;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.capstone.knowledgebridge.extraction.DocxMarkdownConverter;
import com.capstone.knowledgebridge.extraction.ExtractionException;
import com.capstone.knowledgebridge.ingestion.IngestionAttempt;
import com.capstone.knowledgebridge.ingestion.IngestionService;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.video.StoredVideo;
import com.capstone.knowledgebridge.video.VideoStorageService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/knowledge-items")
public class KnowledgeItemController {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeItemController.class);

	// knowledge_item.title and .type are VARCHAR(255); longer values would fail the insert with a 500.
	private static final int MAX_FIELD_LENGTH = 255;

	public record DocumentContentResponse(long id, String title, String type, String text) {
	}

	/** Completed knowledge is readable by signed-in users without exposing management or upload metadata. */
	@GetMapping("/{id}/content")
	public ResponseEntity<DocumentContentResponse> content(@PathVariable Long id) {
		return ResponseEntity.of(knowledgeItemService.findById(id)
				.filter(item -> item.getStatus() == KnowledgeItemStatus.COMPLETED)
				.map(item -> new DocumentContentResponse(item.getId(), item.getTitle(), item.getType(),
						item.getBodyText())));
	}

	// A .docx is a zip that often holds images, so its raw size says little about its text. This only stops oversized
	// uploads early; the converted Markdown still has to fit gbrain's MAX_BODY_BYTES.
	static final long MAX_DOCX_BYTES = 10L * 1024 * 1024;

	public record ManualKnowledgeItemRequest(@NotBlank @Size(max = MAX_FIELD_LENGTH) String title,
			@NotBlank @Size(max = MAX_FIELD_LENGTH) String type, @NotBlank String text) {
	}

	/**
	 * lastErrorCode/lastErrorMessage come from the item's latest ingestion attempt, so they're null unless that attempt
	 * failed - a FAILED item always has them, and a successful retry clears them.
	 */
	public record KnowledgeItemResponse(long id, String title, String type, long ownerId, String status,
			String ingestionStage, String sourceKind, int characterCount, String originalFilename, String sourceSha256,
			Long mediaDurationMs, String transcriptionModel, String transcriptionLanguage, String externalEngineId,
			Integer transcriptionSegmentCount, Instant transcribedAt, String lastErrorCode, String lastErrorMessage,
			Instant createdAt, Instant updatedAt) {
	}

	private final KnowledgeItemService knowledgeItemService;

	private final VideoStorageService videoStorageService;

	private final DocxMarkdownConverter docxMarkdownConverter;

	public KnowledgeItemController(KnowledgeItemService knowledgeItemService, VideoStorageService videoStorageService,
			DocxMarkdownConverter docxMarkdownConverter) {
		this.knowledgeItemService = knowledgeItemService;
		this.videoStorageService = videoStorageService;
		this.docxMarkdownConverter = docxMarkdownConverter;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
	public List<KnowledgeItemResponse> list(@AuthenticationPrincipal Jwt jwt) {
		boolean admin = "ADMIN".equals(jwt.getClaimAsString("role"));
		return toResponses(knowledgeItemService.findAll().stream()
				.filter(item -> admin || item.getStatus() == KnowledgeItemStatus.COMPLETED).toList());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<KnowledgeItemResponse> get(@PathVariable Long id) {
		return knowledgeItemService.findById(id)
				.map(item -> ResponseEntity.ok(toResponse(item)))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/manual")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<KnowledgeItemResponse> createManual(@Valid @RequestBody ManualKnowledgeItemRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		if (IngestionService.exceedsBodyLimit(request.text())) {
			return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
		}
		KnowledgeItem item = knowledgeItemService.createAndIngest(request.title(), request.type(), request.text(),
				Long.parseLong(jwt.getSubject()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(item));
	}

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<KnowledgeItemResponse> uploadDocument(@RequestParam("file") MultipartFile file,
			@RequestParam("title") String title, @RequestParam("type") String type,
			@AuthenticationPrincipal Jwt jwt) {
		if (file.isEmpty() || !isValidField(title) || !isValidField(type)) {
			return ResponseEntity.badRequest().build();
		}
		String filename = file.getOriginalFilename();
		boolean docx = hasExtension(filename, ".docx");
		if (!docx && !hasExtension(filename, ".md")) {
			return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
		}
		// A .docx keeps its filename in knowledge_item.original_filename, VARCHAR(255).
		if (docx && filename.length() > MAX_FIELD_LENGTH) {
			return ResponseEntity.badRequest().build();
		}
		if (file.getSize() > (docx ? MAX_DOCX_BYTES : GbrainDocument.MAX_BODY_BYTES)) {
			return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
		}

		byte[] content = read(file);
		String text;
		if (docx) {
			try {
				text = docxMarkdownConverter.convert(new ByteArrayInputStream(content));
			} catch (ExtractionException ex) {
				log.info("Rejected unreadable .docx upload: {}", ex.getCause().toString());
				return ResponseEntity.badRequest().build();
			}
		} else {
			text = new String(content, StandardCharsets.UTF_8);
		}
		if (text.isBlank()) {
			return ResponseEntity.badRequest().build();
		}
		// The gbrain body limit applies to the converted Markdown, whatever the upload's raw size was.
		if (IngestionService.exceedsBodyLimit(text)) {
			return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
		}

		long ownerId = Long.parseLong(jwt.getSubject());
		KnowledgeItem item = docx
				? knowledgeItemService.createDocxAndIngest(title, type, text, ownerId, filename,
						file.getContentType(), hexDigest("SHA-256", content))
				: knowledgeItemService.createMarkdownAndIngest(title, type, text, ownerId);
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(item));
	}

	@PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<KnowledgeItemResponse> uploadVideo(@RequestParam("file") MultipartFile file,
			@RequestParam("title") String title, @RequestParam("type") String type,
			@AuthenticationPrincipal Jwt jwt) {
		String filename = file.getOriginalFilename();
		if (file.isEmpty() || !isValidField(title) || !isValidField(type) || filename == null
				|| filename.length() > MAX_FIELD_LENGTH) {
			return ResponseEntity.badRequest().build();
		}
		if (!videoStorageService.isSupportedFilename(filename)) {
			return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
		}
		if (videoStorageService.exceedsUploadLimit(file.getSize())) {
			return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
		}

		StoredVideo storedVideo = videoStorageService.store(file);
		try {
			KnowledgeItem item = knowledgeItemService.createVideoAndIngest(title, type,
					Long.parseLong(jwt.getSubject()),
					storedVideo);
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(toResponse(item));
		} catch (RuntimeException ex) {
			videoStorageService.delete(storedVideo.path());
			throw ex;
		}
	}

	@PostMapping("/{id}/retry-ingestion")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<KnowledgeItemResponse> retryIngestion(@PathVariable Long id) {
		Optional<KnowledgeItem> maybeItem = knowledgeItemService.findById(id);
		if (maybeItem.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		KnowledgeItem item = maybeItem.get();
		// Only a FAILED item has anything to retry - PENDING/PROCESSING are already in flight and
		// COMPLETED doesn't need it. 409 Conflict: the request is fine, the resource's state isn't.
		if (item.getStatus() != KnowledgeItemStatus.FAILED) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		item = knowledgeItemService.retry(item);
		return ResponseEntity.ok(toResponse(item));
	}

	private static boolean isValidField(String value) {
		return StringUtils.hasText(value) && value.length() <= MAX_FIELD_LENGTH;
	}

	// Uploads accept Markdown and .docx; .docx is converted to Markdown by the extraction package.
	private static boolean hasExtension(String filename, String extension) {
		return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(extension);
	}

	// An unreadable upload becomes empty content, which is rejected as blank (.md) or unreadable (.docx).
	private static byte[] read(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException ex) {
			log.warn("Could not read uploaded file content: {}", ex.getMessage());
			return new byte[0];
		}
	}

	static String hexDigest(String algorithm, byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(content));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(algorithm + " is not available", ex);
		}
	}

	private KnowledgeItemResponse toResponse(KnowledgeItem item) {
		return toResponses(List.of(item)).get(0);
	}

	private List<KnowledgeItemResponse> toResponses(List<KnowledgeItem> items) {
		Map<Long, IngestionAttempt> latestAttempts = knowledgeItemService
				.findLatestAttempts(items.stream().map(KnowledgeItem::getId).toList());
		return items.stream().map(item -> toResponse(item, latestAttempts.get(item.getId()))).toList();
	}

	private static KnowledgeItemResponse toResponse(KnowledgeItem item, IngestionAttempt latestAttempt) {
		// Successful attempts store null error fields, so only a failed latest attempt surfaces an error.
		String errorCode = latestAttempt == null ? null : latestAttempt.getErrorCode();
		String errorMessage = latestAttempt == null ? null : latestAttempt.getErrorMessage();
		return new KnowledgeItemResponse(item.getId(), item.getTitle(), item.getType(), item.getOwnerId(),
				item.getStatus().name(), item.getIngestionStage().name(), item.getSourceKind().name(),
				item.getCharacterCount(), item.getOriginalFilename(), item.getSourceSha256(), item.getMediaDurationMs(),
				item.getTranscriptionModel(), item.getTranscriptionLanguage(), item.getExternalEngineId(),
				item.getTranscriptionSegmentCount(), item.getTranscribedAt(), errorCode, errorMessage,
				item.getCreatedAt(),
				item.getUpdatedAt());
	}

}
