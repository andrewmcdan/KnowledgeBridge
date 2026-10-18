package com.capstone.knowledgebridge.knowledge;

public enum IngestionStage {
	QUEUED, EXTRACTING_AUDIO, TRANSCRIBING, INDEXING, COMPLETE, FAILED
}
