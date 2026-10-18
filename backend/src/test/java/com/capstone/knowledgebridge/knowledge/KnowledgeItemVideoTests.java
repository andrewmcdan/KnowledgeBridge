package com.capstone.knowledgebridge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KnowledgeItemVideoTests {

	@Test
	void tracksVideoLifecycleAndMetadata() {
		KnowledgeItem item = KnowledgeItem.video("Title", "Meeting", 3L, "meeting.mp4", "video/mp4", "hash",
				"stored.mp4");

		assertThat(item.getSourceKind()).isEqualTo(KnowledgeSourceKind.VIDEO);
		assertThat(item.getIngestionStage()).isEqualTo(IngestionStage.QUEUED);
		assertThat(item.getOriginalFilename()).isEqualTo("meeting.mp4");
		assertThat(item.getSourceContentType()).isEqualTo("video/mp4");
		assertThat(item.getSourceSha256()).isEqualTo("hash");
		assertThat(item.getSourcePath()).isEqualTo("stored.mp4");
		assertThat(item.getMediaDurationMs()).isNull();
		assertThat(item.getTranscriptionModel()).isNull();
		assertThat(item.getTranscriptionLanguage()).isNull();
		assertThat(item.getTranscriptionSegmentCount()).isNull();
		assertThat(item.getTranscribedAt()).isNull();

		item.markProcessing(IngestionStage.EXTRACTING_AUDIO);
		assertThat(item.getStatus()).isEqualTo(KnowledgeItemStatus.PROCESSING);
		item.storeTranscript("hello", 5000, "model", "en", 2);
		assertThat(item.getBodyText()).isEqualTo("hello");
		assertThat(item.getCharacterCount()).isEqualTo(5);
		assertThat(item.getMediaDurationMs()).isEqualTo(5000);
		assertThat(item.getTranscriptionModel()).isEqualTo("model");
		assertThat(item.getTranscriptionLanguage()).isEqualTo("en");
		assertThat(item.getTranscriptionSegmentCount()).isEqualTo(2);
		assertThat(item.getTranscribedAt()).isNotNull();
		item.clearSourcePath();
		assertThat(item.getSourcePath()).isNull();
		item.markQueued();
		assertThat(item.getStatus()).isEqualTo(KnowledgeItemStatus.PENDING);
		item.markCompleted("external");
		assertThat(item.getIngestionStage()).isEqualTo(IngestionStage.COMPLETE);
		item.markFailed();
		assertThat(item.getIngestionStage()).isEqualTo(IngestionStage.FAILED);
	}
}
