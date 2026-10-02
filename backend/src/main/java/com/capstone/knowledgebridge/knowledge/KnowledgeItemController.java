package com.capstone.knowledgebridge.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/knowledge-items")
public class KnowledgeItemController {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeItemController.class);

	public record ManualKnowledgeItemRequest(@NotBlank String title, @NotBlank String type, @NotBlank String text) {
	}

	public record KnowledgeItemResponse(long id, String title, String type, long ownerId, String status,
			int characterCount, String externalEngineId, Instant createdAt, Instant updatedAt) {
	}

	private final KnowledgeItemService knowledgeItemService;

	public KnowledgeItemController(KnowledgeItemService knowledgeItemService) {
		this.knowledgeItemService = knowledgeItemService;
	}

	@PostMapping("/manual")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<KnowledgeItemResponse> createManual(@Valid @RequestBody ManualKnowledgeItemRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		KnowledgeItem item = knowledgeItemService.createAndIngest(request.title(), request.type(), request.text(),
				Long.parseLong(jwt.getSubject()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(item));
	}

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<KnowledgeItemResponse> uploadDocument(@RequestParam("file") MultipartFile file,
			@RequestParam("title") String title, @RequestParam("type") String type,
			@AuthenticationPrincipal Jwt jwt) {
		if (file.isEmpty() || !StringUtils.hasText(title) || !StringUtils.hasText(type)) {
			return ResponseEntity.badRequest().build();
		}
		if (!hasMarkdownExtension(file.getOriginalFilename())) {
			return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
		}

		String text = decode(file);
		if (text.isBlank()) {
			return ResponseEntity.badRequest().build();
		}

		KnowledgeItem item = knowledgeItemService.createAndIngest(title, type, text, Long.parseLong(jwt.getSubject()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(item));
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

	// MVP only supports Markdown uploads per the ingestion README - just .md for now.
	private static boolean hasMarkdownExtension(String filename) {
		return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".md");
	}

	private static String decode(MultipartFile file) {
		try {
			return new String(file.getBytes(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			log.warn("Could not read uploaded file content: {}", ex.getMessage());
			return "";
		}
	}

	private static KnowledgeItemResponse toResponse(KnowledgeItem item) {
		return new KnowledgeItemResponse(item.getId(), item.getTitle(), item.getType(), item.getOwnerId(),
				item.getStatus().name(), item.getCharacterCount(), item.getExternalEngineId(), item.getCreatedAt(),
				item.getUpdatedAt());
	}

}
