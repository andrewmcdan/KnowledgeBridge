package com.capstone.knowledgebridge.video;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public record ExtractedAudio(long durationMs, Path directory, List<Path> segments) implements AutoCloseable {

	@Override
	public void close() {
		try (var paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(ExtractedAudio::delete);
		} catch (IOException ex) {
			throw new VideoIngestionException("AUDIO_CLEANUP_FAILED", "Could not remove derived audio.", ex);
		}
	}

	private static void delete(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ex) {
			throw new VideoIngestionException("AUDIO_CLEANUP_FAILED", "Could not remove derived audio.", ex);
		}
	}
}
