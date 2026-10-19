package com.capstone.knowledgebridge.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.knowledgebridge.ingestion.IngestionService;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemStatus;

class VideoIngestionJobTests {

	@TempDir
	Path temporaryDirectory;

	private final KnowledgeItemRepository repository = mock(KnowledgeItemRepository.class);

	private final VideoMediaService mediaService = mock(VideoMediaService.class);

	private final TranscriptionClient transcriptionClient = mock(TranscriptionClient.class);

	private final VideoStorageService storageService = mock(VideoStorageService.class);

	private final IngestionService ingestionService = mock(IngestionService.class);

	private final VideoIngestionJob job = new VideoIngestionJob(repository, mediaService, transcriptionClient,
			storageService, ingestionService);

	@BeforeEach
	void saveReturnsItsArgument() {
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		doAnswer(invocation -> {
			((KnowledgeItem) invocation.getArgument(0)).markFailed();
			return null;
		}).when(ingestionService).recordFailure(any(), any(Instant.class), any(), any());
	}

	@Test
	void ignoresMissingNonVideoAndCompletedItems() {
		when(repository.findById(1L)).thenReturn(Optional.empty());
		KnowledgeItem manual = item(new KnowledgeItem("Title", "Policy", 1L, "body"), 2L);
		when(repository.findById(2L)).thenReturn(Optional.of(manual));
		KnowledgeItem completed = video(3L, "source.mp4");
		completed.markCompleted("external");
		when(repository.findById(3L)).thenReturn(Optional.of(completed));

		job.run(1L);
		job.run(2L);
		job.run(3L);

		verify(mediaService, never()).extract(any());
		verify(ingestionService, never()).ingest(any());
	}

	@Test
	void transcribesPersistsCleansAndIndexes() throws Exception {
		Path source = Files.writeString(temporaryDirectory.resolve("source.mp4"), "video");
		Path audioDirectory = Files.createDirectory(temporaryDirectory.resolve("audio"));
		Path first = Files.writeString(audioDirectory.resolve("segment-000.mp3"), "one");
		Path second = Files.writeString(audioDirectory.resolve("segment-001.mp3"), "two");
		KnowledgeItem item = video(4L, source.toString());
		when(repository.findById(4L)).thenReturn(Optional.of(item));
		when(mediaService.extract(source)).thenReturn(new ExtractedAudio(1200, audioDirectory, List.of(first, second)));
		when(transcriptionClient.transcribe(first)).thenReturn("first");
		when(transcriptionClient.transcribe(second)).thenReturn("second");
		when(transcriptionClient.model()).thenReturn("model");

		job.run(4L);

		assertThat(item.getBodyText()).isEqualTo("first\n\nsecond");
		assertThat(item.getMediaDurationMs()).isEqualTo(1200);
		assertThat(item.getTranscriptionModel()).isEqualTo("model");
		assertThat(item.getSourcePath()).isNull();
		assertThat(audioDirectory).doesNotExist();
		verify(storageService).delete(source);
		verify(ingestionService).ingest(item);
	}

	@Test
	void resumesIndexingWithoutRetranscribingOrDeletingMissingSource() {
		KnowledgeItem item = video(5L, null);
		item.storeTranscript("existing", 100, "model", null, 1);
		when(repository.findById(5L)).thenReturn(Optional.of(item));

		job.run(5L);

		verify(mediaService, never()).extract(any());
		verify(storageService, never()).delete(any());
		verify(ingestionService).ingest(item);
	}

	@Test
	void recordsMissingSourceAndProcessingFailures() {
		KnowledgeItem missing = video(6L, null);
		when(repository.findById(6L)).thenReturn(Optional.of(missing));
		Path source = temporaryDirectory.resolve("bad.mp4");
		KnowledgeItem bad = video(7L, source.toString());
		when(repository.findById(7L)).thenReturn(Optional.of(bad));
		when(mediaService.extract(source)).thenThrow(new VideoIngestionException("BAD_VIDEO", "bad"));

		job.run(6L);
		job.run(7L);

		assertThat(missing.getStatus()).isEqualTo(KnowledgeItemStatus.FAILED);
		assertThat(bad.getStatus()).isEqualTo(KnowledgeItemStatus.FAILED);
		verify(ingestionService).recordFailure(eq(missing), any(Instant.class), eq("VIDEO_SOURCE_MISSING"), any());
		verify(ingestionService).recordFailure(eq(bad), any(Instant.class), eq("BAD_VIDEO"), eq("bad"));
	}

	@Test
	void rejectsTranscriptThatCannotFitInGbrain() throws Exception {
		Path source = temporaryDirectory.resolve("large.mp4");
		Path audioDirectory = Files.createDirectory(temporaryDirectory.resolve("large-audio"));
		Path segment = Files.writeString(audioDirectory.resolve("segment.mp3"), "x");
		KnowledgeItem item = video(8L, source.toString());
		when(repository.findById(8L)).thenReturn(Optional.of(item));
		when(mediaService.extract(source)).thenReturn(new ExtractedAudio(1, audioDirectory, List.of(segment)));
		when(transcriptionClient.transcribe(segment)).thenReturn("x".repeat(1_048_576));

		job.run(8L);

		verify(ingestionService).recordFailure(eq(item), any(Instant.class), eq("TRANSCRIPT_TOO_LARGE"), any());
		verify(ingestionService, never()).ingest(item);
	}

	private static KnowledgeItem video(long id, String path) {
		return item(KnowledgeItem.video("Title", "Meeting", 1L, "meeting.mp4", "video/mp4", "hash", path), id);
	}

	private static KnowledgeItem item(KnowledgeItem item, long id) {
		ReflectionTestUtils.setField(item, "id", id);
		return item;
	}
}
