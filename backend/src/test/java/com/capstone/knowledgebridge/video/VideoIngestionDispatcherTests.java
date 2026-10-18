package com.capstone.knowledgebridge.video;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemStatus;
import com.capstone.knowledgebridge.knowledge.KnowledgeSourceKind;

class VideoIngestionDispatcherTests {

	@Test
	void dispatchesNewAndRecoveredJobs() {
		VideoIngestionJob job = mock(VideoIngestionJob.class);
		KnowledgeItemRepository repository = mock(KnowledgeItemRepository.class);
		VideoStorageService storageService = mock(VideoStorageService.class);
		KnowledgeItem item = KnowledgeItem.video("Title", "Meeting", 1L, "a.mp4", "video/mp4", "hash", "path");
		ReflectionTestUtils.setField(item, "id", 7L);
		when(repository.findBySourceKindAndStatusIn(KnowledgeSourceKind.VIDEO,
				List.of(KnowledgeItemStatus.PENDING, KnowledgeItemStatus.PROCESSING))).thenReturn(List.of(item));
		VideoIngestionDispatcher dispatcher = new VideoIngestionDispatcher(job, repository, new SyncTaskExecutor(),
				storageService);

		dispatcher.enqueue(6L);
		dispatcher.recoverInterruptedJobs();

		verify(job).run(6L);
		verify(job).run(7L);
		verify(storageService).cleanupDerivedAudio();
	}
}
