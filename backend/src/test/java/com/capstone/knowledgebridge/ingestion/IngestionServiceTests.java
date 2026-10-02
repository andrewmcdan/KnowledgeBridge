package com.capstone.knowledgebridge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.gbrain.model.GbrainWriteResult;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemStatus;
import com.capstone.knowledgebridge.knowledge.KnowledgeRevisionService;

class IngestionServiceTests {

	private final GbrainClient gbrainClient = mock(GbrainClient.class);

	private final IngestionAttemptRepository ingestionAttemptRepository = mock(IngestionAttemptRepository.class);

	private final KnowledgeRevisionService knowledgeRevisionService = mock(KnowledgeRevisionService.class);

	private final IngestionService ingestionService = new IngestionService(gbrainClient, ingestionAttemptRepository,
			knowledgeRevisionService);

	private static KnowledgeItem newItem() {
		KnowledgeItem item = new KnowledgeItem("Title", "Policy", 7L, "body text");
		ReflectionTestUtils.setField(item, "id", 42L);
		return item;
	}

	@Test
	void successfulIngestionMarksCompletedSavesAttemptAndBumpsRevision() {
		KnowledgeItem item = newItem();
		when(ingestionAttemptRepository.countByKnowledgeItemId(42L)).thenReturn(0L);
		when(gbrainClient.upsertDocument(any()))
				.thenReturn(new GbrainWriteResult("42", GbrainWriteResult.Status.WRITTEN, 3));
		when(knowledgeRevisionService.bump()).thenReturn(5L);

		ingestionService.ingest(item);

		assertThat(item.getStatus()).isEqualTo(KnowledgeItemStatus.COMPLETED);
		assertThat(item.getExternalEngineId()).isEqualTo("42");

		ArgumentCaptor<IngestionAttempt> captor = ArgumentCaptor.forClass(IngestionAttempt.class);
		verify(ingestionAttemptRepository).save(captor.capture());
		IngestionAttempt attempt = captor.getValue();
		assertThat(attempt.getKnowledgeItemId()).isEqualTo(42L);
		assertThat(attempt.getAttemptNumber()).isEqualTo(1);
		assertThat(attempt.getStatus()).isEqualTo(IngestionAttemptStatus.COMPLETED);
		assertThat(attempt.getErrorCode()).isNull();
		assertThat(attempt.getErrorMessage()).isNull();
		assertThat(attempt.getStartedAt()).isNotNull();
		assertThat(attempt.getCompletedAt()).isNotNull();

		verify(knowledgeRevisionService).bump();
	}

	@Test
	void retryUsesTheNextAttemptNumber() {
		KnowledgeItem item = newItem();
		when(ingestionAttemptRepository.countByKnowledgeItemId(42L)).thenReturn(1L);
		when(gbrainClient.upsertDocument(any()))
				.thenReturn(new GbrainWriteResult("42", GbrainWriteResult.Status.UNCHANGED, 0));

		ingestionService.ingest(item);

		ArgumentCaptor<IngestionAttempt> captor = ArgumentCaptor.forClass(IngestionAttempt.class);
		verify(ingestionAttemptRepository).save(captor.capture());
		assertThat(captor.getValue().getAttemptNumber()).isEqualTo(2);
	}

	@Test
	void failedIngestionMarksFailedAndDoesNotBumpRevision() {
		KnowledgeItem item = newItem();
		when(ingestionAttemptRepository.countByKnowledgeItemId(42L)).thenReturn(0L);
		when(gbrainClient.upsertDocument(any()))
				.thenThrow(new GbrainException(GbrainErrorCode.UNAVAILABLE, "Could not reach the knowledge engine."));

		ingestionService.ingest(item);

		assertThat(item.getStatus()).isEqualTo(KnowledgeItemStatus.FAILED);

		ArgumentCaptor<IngestionAttempt> captor = ArgumentCaptor.forClass(IngestionAttempt.class);
		verify(ingestionAttemptRepository).save(captor.capture());
		IngestionAttempt attempt = captor.getValue();
		assertThat(attempt.getStatus()).isEqualTo(IngestionAttemptStatus.FAILED);
		assertThat(attempt.getErrorCode()).isEqualTo("UNAVAILABLE");
		assertThat(attempt.getErrorMessage()).isEqualTo("Could not reach the knowledge engine.");

		verify(knowledgeRevisionService, never()).bump();
	}

	@Test
	void failedIngestionAppendsUpstreamCodeToMessageWhenPresent() {
		KnowledgeItem item = newItem();
		when(ingestionAttemptRepository.countByKnowledgeItemId(42L)).thenReturn(0L);
		when(gbrainClient.upsertDocument(any())).thenThrow(new GbrainException(GbrainErrorCode.NOT_FOUND,
				"Page not found.", "page_not_found", Duration.ofSeconds(5), null));

		ingestionService.ingest(item);

		ArgumentCaptor<IngestionAttempt> captor = ArgumentCaptor.forClass(IngestionAttempt.class);
		verify(ingestionAttemptRepository).save(captor.capture());
		assertThat(captor.getValue().getErrorMessage()).isEqualTo("Page not found. (gbrain: page_not_found)");
	}

	@Test
	void failedIngestionTruncatesOverlyLongMessages() {
		KnowledgeItem item = newItem();
		when(ingestionAttemptRepository.countByKnowledgeItemId(42L)).thenReturn(0L);
		String longMessage = "x".repeat(1100);
		when(gbrainClient.upsertDocument(any())).thenThrow(new GbrainException(GbrainErrorCode.ENGINE, longMessage));

		ingestionService.ingest(item);

		ArgumentCaptor<IngestionAttempt> captor = ArgumentCaptor.forClass(IngestionAttempt.class);
		verify(ingestionAttemptRepository).save(captor.capture());
		assertThat(captor.getValue().getErrorMessage()).hasSize(1000);
	}

	@Test
	void failedIngestionHandlesNullMessage() {
		KnowledgeItem item = newItem();
		when(ingestionAttemptRepository.countByKnowledgeItemId(42L)).thenReturn(0L);
		when(gbrainClient.upsertDocument(any())).thenThrow(new GbrainException(GbrainErrorCode.ENGINE, null));

		ingestionService.ingest(item);

		ArgumentCaptor<IngestionAttempt> captor = ArgumentCaptor.forClass(IngestionAttempt.class);
		verify(ingestionAttemptRepository).save(captor.capture());
		assertThat(captor.getValue().getErrorMessage()).isNull();
	}

}
