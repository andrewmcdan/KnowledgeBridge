package com.capstone.knowledgebridge.video;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import com.github.kokorin.jaffree.ffprobe.FFprobe;
import com.github.kokorin.jaffree.ffprobe.FFprobeResult;

@Service
public class JaffreeVideoMediaService implements VideoMediaService {

	private final VideoProperties properties;

	public JaffreeVideoMediaService(VideoProperties properties) {
		this.properties = properties;
	}

	@Override
	public ExtractedAudio extract(Path video) {
		Path directory = null;
		try {
			FFprobeResult probe = new FFprobe(properties.ffprobePath()).setShowStreams(true).setShowFormat(true)
					.setInput(video).execute();
			if (probe.getStreams().stream().noneMatch(stream -> stream.getCodecType() == StreamType.AUDIO)) {
				throw new VideoIngestionException("VIDEO_HAS_NO_AUDIO", "The uploaded video has no audio stream.");
			}
			Float durationSeconds = probe.getFormat().getDuration();
			if (durationSeconds == null || durationSeconds <= 0) {
				throw new VideoIngestionException("VIDEO_DURATION_INVALID",
						"The video duration could not be determined.");
			}
			long durationMs = Math.round(durationSeconds * 1000.0);
			if (durationMs > properties.maxDuration().toMillis()) {
				throw new VideoIngestionException("VIDEO_TOO_LONG", "The video exceeds the configured duration limit.");
			}

			Files.createDirectories(properties.storageDirectory());
			directory = Files.createTempDirectory(properties.storageDirectory(), "audio-");
			Path outputPattern = directory.resolve("segment-%03d.mp3");
			UrlOutput output = UrlOutput.toPath(outputPattern).addArguments("-map", "0:a:0")
					.disableStream(StreamType.VIDEO).addArguments("-ac", "1").addArguments("-ar", "16000")
					.addArguments("-b:a", "48k").addArguments("-f", "segment")
					.addArguments("-segment_time", Long.toString(properties.segmentDuration().toSeconds()))
					.addArguments("-reset_timestamps", "1");
			new FFmpeg(properties.ffmpegPath()).setOverwriteOutput(true).addInput(UrlInput.fromPath(video))
					.addOutput(output)
					.execute();
			List<Path> segments;
			try (var paths = Files.list(directory)) {
				segments = paths.filter(path -> path.getFileName().toString().endsWith(".mp3"))
						.sorted(Comparator.naturalOrder()).toList();
			}
			if (segments.isEmpty()) {
				throw new VideoIngestionException("AUDIO_EXTRACTION_FAILED", "Audio extraction produced no output.");
			}
			return new ExtractedAudio(durationMs, directory, segments);
		} catch (VideoIngestionException ex) {
			cleanup(directory);
			throw ex;
		} catch (IOException | RuntimeException ex) {
			cleanup(directory);
			throw new VideoIngestionException("AUDIO_EXTRACTION_FAILED",
					"Could not inspect or extract the video audio.",
					ex);
		}
	}

	private static void cleanup(Path directory) {
		if (directory != null) {
			new ExtractedAudio(0, directory, List.of()).close();
		}
	}
}
