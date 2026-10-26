package com.capstone.knowledgebridge.knowledge;

public enum IngestionStage {
	QUEUED, EXTRACTING_AUDIO, TRANSCRIBING, GENERATING_TITLE, INDEXING, COMPLETE, FAILED
}
