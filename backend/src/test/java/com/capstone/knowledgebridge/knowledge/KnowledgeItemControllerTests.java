package com.capstone.knowledgebridge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.capstone.knowledgebridge.knowledge.KnowledgeItemController.KnowledgeItemResponse;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemController.ManualKnowledgeItemRequest;

class KnowledgeItemControllerTests {

	private final KnowledgeItemService knowledgeItemService = mock(KnowledgeItemService.class);

	private final KnowledgeItemController controller = new KnowledgeItemController(knowledgeItemService);

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
	void uploadRejectsNonMarkdownExtension() {
		MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf",
				"content".getBytes(StandardCharsets.UTF_8));

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
