package com.capstone.knowledgebridge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.capstone.knowledgebridge.ingestion.IngestionService;

class KnowledgeItemServiceTests {

	private final KnowledgeItemRepository knowledgeItemRepository = mock(KnowledgeItemRepository.class);

	private final IngestionService ingestionService = mock(IngestionService.class);

	private final KnowledgeItemService knowledgeItemService = new KnowledgeItemService(knowledgeItemRepository,
			ingestionService);

	@Test
	void createAndIngestTrimsFieldsSavesTwiceAndCallsIngestion() {
		when(knowledgeItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		KnowledgeItem result = knowledgeItemService.createAndIngest("  Title  ", "  Policy  ", "body text", 7L);

		assertThat(result.getTitle()).isEqualTo("Title");
		assertThat(result.getType()).isEqualTo("Policy");
		assertThat(result.getBodyText()).isEqualTo("body text");
		assertThat(result.getOwnerId()).isEqualTo(7L);

		verify(knowledgeItemRepository, times(2)).save(any());
		ArgumentCaptor<KnowledgeItem> captor = ArgumentCaptor.forClass(KnowledgeItem.class);
		verify(ingestionService).ingest(captor.capture());
		assertThat(captor.getValue().getTitle()).isEqualTo("Title");
	}

	@Test
	void findByIdDelegatesToRepository() {
		KnowledgeItem item = new KnowledgeItem("Title", "Policy", 7L, "body");
		when(knowledgeItemRepository.findById(1L)).thenReturn(Optional.of(item));
		when(knowledgeItemRepository.findById(2L)).thenReturn(Optional.empty());

		assertThat(knowledgeItemService.findById(1L)).contains(item);
		assertThat(knowledgeItemService.findById(2L)).isEmpty();
	}

	@Test
	void retryCallsIngestionAndSaves() {
		KnowledgeItem item = new KnowledgeItem("Title", "Policy", 7L, "body");
		when(knowledgeItemRepository.save(item)).thenReturn(item);

		KnowledgeItem result = knowledgeItemService.retry(item);

		assertThat(result).isSameAs(item);
		verify(ingestionService).ingest(item);
		verify(knowledgeItemRepository).save(item);
	}

}
