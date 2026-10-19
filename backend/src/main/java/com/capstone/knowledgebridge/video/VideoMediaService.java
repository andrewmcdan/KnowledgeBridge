package com.capstone.knowledgebridge.video;

import java.nio.file.Path;

public interface VideoMediaService {

	ExtractedAudio extract(Path video);
}
