package com.capstone.knowledgebridge.video;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemStatus;
import com.capstone.knowledgebridge.knowledge.KnowledgeSourceKind;

@Service
public class VideoIngestionDispatcher {

	private final VideoIngestionJob job;

	private final KnowledgeItemRepository knowledgeItemRepository;

	private final TaskExecutor taskExecutor;

	private final VideoStorageService videoStorageService;

	public VideoIngestionDispatcher(VideoIngestionJob job, KnowledgeItemRepository knowledgeItemRepository,
			@Qualifier("videoIngestionExecutor") TaskExecutor taskExecutor, VideoStorageService videoStorageService) {
		this.job = job;
		this.knowledgeItemRepository = knowledgeItemRepository;
		this.taskExecutor = taskExecutor;
		this.videoStorageService = videoStorageService;
	}

	public void enqueue(Long itemId) {
		taskExecutor.execute(() -> job.run(itemId));
	}

	@EventListener(ApplicationReadyEvent.class)
	public void recoverInterruptedJobs() {
		videoStorageService.cleanupDerivedAudio();
		knowledgeItemRepository
				.findBySourceKindAndStatusIn(KnowledgeSourceKind.VIDEO,
						List.of(KnowledgeItemStatus.PENDING, KnowledgeItemStatus.PROCESSING))
				.forEach(item -> enqueue(item.getId()));
	}
}
