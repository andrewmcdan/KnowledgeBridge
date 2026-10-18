package com.capstone.knowledgebridge.video;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.capstone.knowledgebridge.ingestion.IngestionService;
import com.capstone.knowledgebridge.knowledge.IngestionStage;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemStatus;
import com.capstone.knowledgebridge.knowledge.KnowledgeSourceKind;

@Service
public class VideoIngestionJob {

	private final KnowledgeItemRepository knowledgeItemRepository;

	private final VideoMediaService videoMediaService;

	private final TranscriptionClient transcriptionClient;

	private final VideoStorageService videoStorageService;

	private final IngestionService ingestionService;

	public VideoIngestionJob(KnowledgeItemRepository knowledgeItemRepository, VideoMediaService videoMediaService,
			TranscriptionClient transcriptionClient, VideoStorageService videoStorageService,
			IngestionService ingestionService) {
		this.knowledgeItemRepository = knowledgeItemRepository;
		this.videoMediaService = videoMediaService;
		this.transcriptionClient = transcriptionClient;
		this.videoStorageService = videoStorageService;
		this.ingestionService = ingestionService;
	}

	public void run(Long itemId) {
		KnowledgeItem item = knowledgeItemRepository.findById(itemId).orElse(null);
		if (item == null || item.getSourceKind() != KnowledgeSourceKind.VIDEO
				|| item.getStatus() == KnowledgeItemStatus.COMPLETED) {
			return;
		}
		Instant startedAt = Instant.now();
		try {
			if (item.getBodyText().isBlank()) {
				transcribe(item);
			}
			deleteSource(item);
			ingestionService.ingest(item);
			knowledgeItemRepository.save(item);
		} catch (VideoIngestionException ex) {
			ingestionService.recordFailure(item, startedAt, ex.getCode(), ex.getMessage());
			knowledgeItemRepository.save(item);
		}
	}

	private void transcribe(KnowledgeItem item) {
		if (item.getSourcePath() == null) {
			throw new VideoIngestionException("VIDEO_SOURCE_MISSING", "The retained source video is unavailable.");
		}
		item.markProcessing(IngestionStage.EXTRACTING_AUDIO);
		knowledgeItemRepository.save(item);
		try (ExtractedAudio audio = videoMediaService.extract(Path.of(item.getSourcePath()))) {
			item.markProcessing(IngestionStage.TRANSCRIBING);
			knowledgeItemRepository.save(item);
			List<String> transcripts = new ArrayList<>();
			for (Path segment : audio.segments()) {
				transcripts.add(transcriptionClient.transcribe(segment));
			}
			String transcript = String.join("\n\n", transcripts);
			if (IngestionService.exceedsBodyLimit(transcript)) {
				throw new VideoIngestionException("TRANSCRIPT_TOO_LARGE",
						"The transcript exceeds the knowledge engine body limit.");
			}
			item.storeTranscript(transcript, audio.durationMs(), transcriptionClient.model(), null,
					audio.segments().size());
			knowledgeItemRepository.save(item);
		}
	}

	private void deleteSource(KnowledgeItem item) {
		if (item.getSourcePath() != null) {
			videoStorageService.delete(Path.of(item.getSourcePath()));
			item.clearSourcePath();
			knowledgeItemRepository.save(item);
		}
	}
}
