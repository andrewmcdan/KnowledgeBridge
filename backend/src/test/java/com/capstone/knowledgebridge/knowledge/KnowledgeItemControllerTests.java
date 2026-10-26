package com.capstone.knowledgebridge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.capstone.knowledgebridge.extraction.DocxFixtures;
import com.capstone.knowledgebridge.extraction.DocxMarkdownConverter;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.ingestion.IngestionAttempt;
import com.capstone.knowledgebridge.video.VideoStorageService;
import com.capstone.knowledgebridge.video.StoredVideo;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemController.KnowledgeItemResponse;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemController.ManualKnowledgeItemRequest;

class KnowledgeItemControllerTests {

	private final KnowledgeItemService knowledgeItemService = mock(KnowledgeItemService.class);

	private final VideoStorageService videoStorageService = mock(VideoStorageService.class);

	private final ManualNoteEditor manualNoteEditor = mock(ManualNoteEditor.class);

	private final KnowledgeItemController controller = new KnowledgeItemController(knowledgeItemService,
			videoStorageService, new DocxMarkdownConverter(), manualNoteEditor);

	private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

	private static final String OVER_GBRAIN_LIMIT = "a".repeat(GbrainDocument.MAX_BODY_BYTES);

	private static Jwt jwtFor(String subject) {
		Jwt jwt = mock(Jwt.class);
		when(jwt.getSubject()).thenReturn(subject);
		when(jwt.getClaimAsString("role")).thenReturn("ADMIN");
		return jwt;
	}

	@Test
	void createManualReturnsCreatedWithMappedResponse() {
		KnowledgeItem item = new KnowledgeItem("Title", "Policy", 7L, "body text");
		ReflectionTestUtils.setField(item, "id", 1L);
		when(knowledgeItemService.createAndIngest("Title", "Policy", "body text", 7L)).thenReturn(item);

		ResponseEntity<KnowledgeItemResponse> response = controller
				.createManual(new ManualKnowledgeItemRequest("Title", "Policy", "body text"), jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().title()).isEqualTo("Title");
		assertThat(response.getBody().status()).isEqualTo("PENDING");
	}

	@Test
	void listReturnsItemsWithTheirLatestFailure() {
		KnowledgeItem failed = new KnowledgeItem("Failed", "Policy", 7L, "body");
		ReflectionTestUtils.setField(failed, "id", 1L);
		failed.markFailed();
		KnowledgeItem pending = new KnowledgeItem("Pending", "Policy", 7L, "body");
		ReflectionTestUtils.setField(pending, "id", 2L);
		when(knowledgeItemService.findAll()).thenReturn(List.of(failed, pending));
		when(knowledgeItemService.findLatestAttempts(List.of(1L, 2L))).thenReturn(
				Map.of(1L, IngestionAttempt.failed(1L, 1, Instant.now(), "TIMEOUT", "gbrain timed out")));

		List<KnowledgeItemResponse> responses = controller.list(jwtFor("7"));

		assertThat(responses).extracting(KnowledgeItemResponse::title).containsExactly("Failed", "Pending");
		assertThat(responses.get(0).status()).isEqualTo("FAILED");
		assertThat(responses.get(0).lastErrorCode()).isEqualTo("TIMEOUT");
		assertThat(responses.get(0).lastErrorMessage()).isEqualTo("gbrain timed out");
		assertThat(responses.get(1).lastErrorCode()).isNull();
		assertThat(responses.get(1).lastErrorMessage()).isNull();
	}

	@Test
	void getReturnsItemOrNotFound() {
		KnowledgeItem item = new KnowledgeItem("Title", "Policy", 7L, "body");
		ReflectionTestUtils.setField(item, "id", 1L);
		when(knowledgeItemService.findById(1L)).thenReturn(Optional.of(item));
		when(knowledgeItemService.findById(2L)).thenReturn(Optional.empty());

		ResponseEntity<KnowledgeItemResponse> found = controller.get(1L);

		assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(found.getBody().id()).isEqualTo(1L);
		assertThat(controller.get(2L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void normalUsersListOnlyCompletedItems() {
		KnowledgeItem completed = new KnowledgeItem("Ready", "Policy", 7L, "body");
		ReflectionTestUtils.setField(completed, "id", 1L);
		completed.markCompleted("engine-id");
		KnowledgeItem pending = new KnowledgeItem("Pending", "Policy", 7L, "body");
		ReflectionTestUtils.setField(pending, "id", 2L);
		when(knowledgeItemService.findAll()).thenReturn(List.of(completed, pending));
		Jwt user = jwtFor("7");
		when(user.getClaimAsString("role")).thenReturn("USER");

		assertThat(controller.list(user)).extracting(KnowledgeItemResponse::title).containsExactly("Ready");
		verify(knowledgeItemService).findLatestAttempts(List.of(1L));
	}

	@Test
	void createManualRejectsBodyOverGbrainLimit() {
		ResponseEntity<KnowledgeItemResponse> response = controller
				.createManual(new ManualKnowledgeItemRequest("Title", "Policy", OVER_GBRAIN_LIMIT), jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
	}

	@Test
	void uploadRejectsEmptyFile() {
		MultipartFile file = new MockMultipartFile("file", "doc.md", "text/markdown", new byte[0]);

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Policy",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void uploadRejectsBlankTitle() {
		MultipartFile file = new MockMultipartFile("file", "doc.md", "text/markdown",
				"content".getBytes(StandardCharsets.UTF_8));

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "  ", "Policy", jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void uploadRejectsBlankType() {
		MultipartFile file = new MockMultipartFile("file", "doc.md", "text/markdown",
				"content".getBytes(StandardCharsets.UTF_8));

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "  ", jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void uploadRejectsOverlongTitleAndType() {
		MultipartFile file = new MockMultipartFile("file", "doc.md", "text/markdown",
				"content".getBytes(StandardCharsets.UTF_8));
		String overlong = "x".repeat(256);

		assertThat(controller.uploadDocument(file, overlong, "Policy", jwtFor("7")).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(controller.uploadDocument(file, "Title", overlong, jwtFor("7")).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void uploadRejectsBodyOverGbrainLimit() {
		MultipartFile file = new MockMultipartFile("file", "doc.md", "text/markdown",
				OVER_GBRAIN_LIMIT.getBytes(StandardCharsets.UTF_8));

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Policy",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
	}

	@Test
	void uploadRejectsNonMarkdownExtension() {
		MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf",
				"content".getBytes(StandardCharsets.UTF_8));

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Policy",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	void uploadRejectsMissingFilename() {
		// MockMultipartFile turns a null filename into "", so mock the interface to reach the null check.
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getOriginalFilename()).thenReturn(null);

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Policy",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	void uploadRejectsBlankContentAfterDecoding() {
		MultipartFile file = new MockMultipartFile("file", "doc.md", "text/markdown",
				"   \n  ".getBytes(StandardCharsets.UTF_8));

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Policy",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void uploadRejectsOversizedMarkdownBeforeReadingIt() throws IOException {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getOriginalFilename()).thenReturn("doc.md");
		when(file.getSize()).thenReturn((long) GbrainDocument.MAX_BODY_BYTES + 1);

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Policy",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		verify(file, never()).getBytes();
	}

	@Test
	void uploadRejectsUnreadableFile() throws IOException {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getOriginalFilename()).thenReturn("doc.md");
		when(file.getBytes()).thenThrow(new IOException("disk exploded"));

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Policy",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void uploadHappyPathReturnsCreated() {
		MultipartFile file = new MockMultipartFile("file", "doc.md", "text/markdown",
				"# Hello".getBytes(StandardCharsets.UTF_8));
		KnowledgeItem item = new KnowledgeItem("Title", "Policy", 7L, "# Hello");
		ReflectionTestUtils.setField(item, "id", 1L);
		when(knowledgeItemService.createMarkdownAndIngest(eq("Title"), eq("Policy"), anyString(), eq(7L)))
				.thenReturn(item);

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Policy",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	@Test
	void docxUploadIsConvertedToMarkdownAndSavedWithItsSourceMetadata() {
		byte[] docx = DocxFixtures.docx(document -> {
			DocxFixtures.style(document, "Heading1", "heading 1");
			DocxFixtures.styled(document, "Heading1", "Equipment");
			DocxFixtures.paragraph(document, "Monitors are replaced every 5 years.");
		});
		MultipartFile file = new MockMultipartFile("file", "Equipment Guide.DOCX", DOCX_TYPE, docx);
		String sha256 = KnowledgeItemController.hexDigest("SHA-256", docx);
		KnowledgeItem item = KnowledgeItem.docx("Title", "Guide", 7L, "markdown", "Equipment Guide.DOCX", DOCX_TYPE,
				sha256);
		ReflectionTestUtils.setField(item, "id", 1L);
		when(knowledgeItemService.createDocxAndIngest("Title", "Guide",
				"# Equipment\n\nMonitors are replaced every 5 years.\n", 7L, "Equipment Guide.DOCX", DOCX_TYPE,
				sha256)).thenReturn(item);

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Guide",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().sourceKind()).isEqualTo("DOCX");
		assertThat(response.getBody().originalFilename()).isEqualTo("Equipment Guide.DOCX");
		assertThat(sha256).hasSize(64);
	}

	@Test
	void docxUploadRejectsAFileThatIsNotAWordDocument() {
		MultipartFile file = new MockMultipartFile("file", "renamed.docx", DOCX_TYPE,
				"# Markdown in disguise".getBytes(StandardCharsets.UTF_8));

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Guide",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		verify(knowledgeItemService, never()).createDocxAndIngest(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void docxUploadRejectsADocumentWithoutText() {
		MultipartFile file = new MockMultipartFile("file", "blank.docx", DOCX_TYPE,
				DocxFixtures.docx(document -> document.createParagraph()));

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Guide",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void docxUploadAppliesGbrainsBodyLimitToTheConvertedMarkdown() {
		// Text compresses, so the raw .docx is below even the Markdown limit while the converted Markdown is over it.
		// Random letters rather than one repeated character, which POI's zip bomb check would reject.
		Random random = new Random(42);
		StringBuilder text = new StringBuilder();
		while (text.length() <= GbrainDocument.MAX_BODY_BYTES) {
			text.append((char) ('a' + random.nextInt(26)));
		}
		byte[] docx = DocxFixtures.docx(document -> DocxFixtures.paragraph(document, text.toString()));
		MultipartFile file = new MockMultipartFile("file", "huge.docx", DOCX_TYPE, docx);

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Guide",
				jwtFor("7"));

		assertThat(docx.length).isLessThan(GbrainDocument.MAX_BODY_BYTES);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
	}

	@Test
	void docxUploadAllowsMoreThanTheMarkdownRawLimitButRejectsOverTenMegabytes() throws IOException {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getOriginalFilename()).thenReturn("big.docx");
		when(file.getSize()).thenReturn(KnowledgeItemController.MAX_DOCX_BYTES + 1);

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Guide",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		verify(file, never()).getBytes();

		// Over the Markdown limit but under the .docx limit: the size check passes and the file is read.
		when(file.getSize()).thenReturn((long) GbrainDocument.MAX_BODY_BYTES + 1);
		when(file.getBytes()).thenReturn(new byte[0]);
		assertThat(controller.uploadDocument(file, "Title", "Guide", jwtFor("7")).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		verify(file).getBytes();
	}

	@Test
	void docxUploadRejectsAFilenameTooLongToStore() {
		MultipartFile file = new MockMultipartFile("file", "x".repeat(251) + ".docx", DOCX_TYPE, new byte[]{1});

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Guide",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void hexDigestFailsLoudlyForAnUnknownAlgorithm() {
		assertThatThrownBy(() -> KnowledgeItemController.hexDigest("NOPE-1", new byte[0]))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("NOPE-1");
	}

	@Test
	void videoUploadRejectsEachInvalidRequestField() {
		MultipartFile empty = new MockMultipartFile("file", "meeting.mp4", "video/mp4", new byte[0]);
		assertThat(controller.uploadVideo(empty, "Title", "Meeting", jwtFor("7")).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);

		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getOriginalFilename()).thenReturn("meeting.mp4");
		assertThat(controller.uploadVideo(file, " ", "Meeting", jwtFor("7")).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(controller.uploadVideo(file, "Title", " ", jwtFor("7")).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		when(file.getOriginalFilename()).thenReturn(null);
		assertThat(controller.uploadVideo(file, "Title", "Meeting", jwtFor("7")).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		when(file.getOriginalFilename()).thenReturn("x".repeat(256));
		assertThat(controller.uploadVideo(file, "Title", "Meeting", jwtFor("7")).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void videoUploadRejectsUnsupportedAndOversizedFiles() {
		MultipartFile file = new MockMultipartFile("file", "meeting.mp4", "video/mp4", new byte[]{1});
		when(videoStorageService.isSupportedFilename("meeting.mp4")).thenReturn(false).thenReturn(true);
		when(videoStorageService.exceedsUploadLimit(1)).thenReturn(true);

		assertThat(controller.uploadVideo(file, "Title", "Meeting", jwtFor("7")).getStatusCode())
				.isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertThat(controller.uploadVideo(file, "Title", "Meeting", jwtFor("7")).getStatusCode())
				.isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
	}

	@Test
	void videoUploadStoresAndQueuesAcceptedItem() {
		MultipartFile file = new MockMultipartFile("file", "meeting.mp4", "video/mp4", new byte[]{1});
		StoredVideo stored = new StoredVideo("meeting.mp4", "video/mp4", "hash", Path.of("stored"));
		KnowledgeItem item = KnowledgeItem.video("Title", "Meeting", 7L, "meeting.mp4", "video/mp4", "hash",
				"stored");
		ReflectionTestUtils.setField(item, "id", 2L);
		when(videoStorageService.isSupportedFilename("meeting.mp4")).thenReturn(true);
		when(videoStorageService.store(file)).thenReturn(stored);
		when(knowledgeItemService.createVideoAndIngest("Title", "Meeting", 7L, stored)).thenReturn(item);

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadVideo(file, "Title", "Meeting", jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(response.getBody().sourceKind()).isEqualTo("VIDEO");
		assertThat(response.getBody().originalFilename()).isEqualTo("meeting.mp4");
	}

	@Test
	void videoUploadDeletesStoredFileWhenDatabaseCreationFails() {
		MultipartFile file = new MockMultipartFile("file", "meeting.mp4", "video/mp4", new byte[]{1});
		StoredVideo stored = new StoredVideo("meeting.mp4", "video/mp4", "hash", Path.of("stored"));
		when(videoStorageService.isSupportedFilename("meeting.mp4")).thenReturn(true);
		when(videoStorageService.store(file)).thenReturn(stored);
		when(knowledgeItemService.createVideoAndIngest("Title", "Meeting", 7L, stored))
				.thenThrow(new IllegalStateException("database down"));

		assertThatThrownBy(() -> controller.uploadVideo(file, "Title", "Meeting", jwtFor("7")))
				.isInstanceOf(IllegalStateException.class);
		verify(videoStorageService).delete(Path.of("stored"));
	}

	@Test
	void retryIngestionReturnsNotFoundWhenItemMissing() {
		when(knowledgeItemService.findById(99L)).thenReturn(Optional.empty());

		ResponseEntity<KnowledgeItemResponse> response = controller.retryIngestion(99L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void retryIngestionReturnsConflictWhenNotFailed() {
		KnowledgeItem item = new KnowledgeItem("Title", "Policy", 7L, "body");
		when(knowledgeItemService.findById(1L)).thenReturn(Optional.of(item));

		ResponseEntity<KnowledgeItemResponse> response = controller.retryIngestion(1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void retryIngestionReturnsOkWhenRetried() {
		KnowledgeItem item = new KnowledgeItem("Title", "Policy", 7L, "body");
		ReflectionTestUtils.setField(item, "id", 1L);
		item.markProcessing();
		item.markFailed();
		when(knowledgeItemService.findById(1L)).thenReturn(Optional.of(item));
		when(manualNoteEditor.prepareRetry(1L, 1L)).thenReturn(Optional.of(item));
		when(knowledgeItemService.retry(item)).thenReturn(item);

		ResponseEntity<KnowledgeItemResponse> response = controller.retryIngestion(1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void retryRejectsANoteClaimedByAnEditorButAllowsFailedUploads() {
		KnowledgeItem note = new KnowledgeItem("Title", "Note", 7L, "body");
		note.markFailed();
		when(knowledgeItemService.findById(1L)).thenReturn(Optional.of(note));
		assertThat(controller.retryIngestion(1L).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		verify(knowledgeItemService, never()).retry(note);
		KnowledgeItem upload = new KnowledgeItem("Title", "Note", 7L, "body", KnowledgeSourceKind.MARKDOWN);
		ReflectionTestUtils.setField(upload, "id", 2L);
		upload.markFailed();
		when(knowledgeItemService.findById(2L)).thenReturn(Optional.of(upload));
		when(knowledgeItemService.retry(upload)).thenReturn(upload);
		assertThat(controller.retryIngestion(2L).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

}
