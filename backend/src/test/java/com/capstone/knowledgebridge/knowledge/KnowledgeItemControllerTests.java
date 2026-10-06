package com.capstone.knowledgebridge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.ingestion.IngestionAttempt;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemController.KnowledgeItemResponse;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemController.ManualKnowledgeItemRequest;

class KnowledgeItemControllerTests {

	private final KnowledgeItemService knowledgeItemService = mock(KnowledgeItemService.class);

	private final KnowledgeItemController controller = new KnowledgeItemController(knowledgeItemService);

	private static final String OVER_GBRAIN_LIMIT = "a".repeat(GbrainDocument.MAX_BODY_BYTES);

	private static Jwt jwtFor(String subject) {
		Jwt jwt = mock(Jwt.class);
		when(jwt.getSubject()).thenReturn(subject);
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

		List<KnowledgeItemResponse> responses = controller.list();

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
		when(knowledgeItemService.createAndIngest(eq("Title"), eq("Policy"), anyString(), eq(7L))).thenReturn(item);

		ResponseEntity<KnowledgeItemResponse> response = controller.uploadDocument(file, "Title", "Policy",
				jwtFor("7"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
		when(knowledgeItemService.retry(item)).thenReturn(item);

		ResponseEntity<KnowledgeItemResponse> response = controller.retryIngestion(1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

}
