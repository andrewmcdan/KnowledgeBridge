package com.capstone.knowledgebridge.video;

import java.nio.file.Path;

public interface TranscriptionClient {

	String transcribe(Path audioSegment);

	String model();
}
