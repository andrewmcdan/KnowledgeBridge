package com.capstone.knowledgebridge.video;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.util.unit.DataSize;

class VideoPropertiesTests {

	private static final Path PATH = Path.of("tool");

	@Test
	void acceptsValidProperties() {
		new VideoProperties(Path.of("uploads"), DataSize.ofMegabytes(500), Duration.ofHours(2),
				Duration.ofMinutes(20), PATH, PATH);
		new TranscriptionProperties("key", URI.create("https://api.openai.com/v1"), "model", Duration.ofMinutes(1));
		new IngestionWorkerProperties(1, 1);
	}

	@ParameterizedTest
	@MethodSource("invalidVideoProperties")
	void rejectsInvalidVideoProperties(VideoPropertiesArguments values) {
		assertThatThrownBy(
				() -> new VideoProperties(values.storage(), values.size(), values.maximum(), values.segment(),
						values.ffmpeg(), values.ffprobe()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	static Stream<VideoPropertiesArguments> invalidVideoProperties() {
		return Stream.of(new VideoPropertiesArguments(null, DataSize.ofBytes(1), Duration.ofSeconds(2),
				Duration.ofSeconds(1), PATH, PATH),
				new VideoPropertiesArguments(Path.of("u"), null, Duration.ofSeconds(2), Duration.ofSeconds(1), PATH,
						PATH),
				new VideoPropertiesArguments(Path.of("u"), DataSize.ofBytes(0), Duration.ofSeconds(2),
						Duration.ofSeconds(1), PATH, PATH),
				new VideoPropertiesArguments(Path.of("u"), DataSize.ofBytes(1), null, Duration.ofSeconds(1), PATH,
						PATH),
				new VideoPropertiesArguments(Path.of("u"), DataSize.ofBytes(1), Duration.ZERO, Duration.ofSeconds(1),
						PATH, PATH),
				new VideoPropertiesArguments(Path.of("u"), DataSize.ofBytes(1), Duration.ofSeconds(-1),
						Duration.ofSeconds(1), PATH, PATH),
				new VideoPropertiesArguments(Path.of("u"), DataSize.ofBytes(1), Duration.ofSeconds(2), null, PATH,
						PATH),
				new VideoPropertiesArguments(Path.of("u"), DataSize.ofBytes(1), Duration.ofSeconds(2), Duration.ZERO,
						PATH, PATH),
				new VideoPropertiesArguments(Path.of("u"), DataSize.ofBytes(1), Duration.ofSeconds(2),
						Duration.ofSeconds(-1), PATH, PATH),
				new VideoPropertiesArguments(Path.of("u"), DataSize.ofBytes(1), Duration.ofSeconds(2),
						Duration.ofSeconds(3), PATH, PATH),
				new VideoPropertiesArguments(Path.of("u"), DataSize.ofBytes(1), Duration.ofSeconds(2),
						Duration.ofSeconds(1), null, PATH),
				new VideoPropertiesArguments(Path.of("u"), DataSize.ofBytes(1), Duration.ofSeconds(2),
						Duration.ofSeconds(1), PATH, null));
	}

	@ParameterizedTest
	@MethodSource("invalidTranscriptionProperties")
	void rejectsInvalidTranscriptionProperties(TranscriptionArguments values) {
		assertThatThrownBy(() -> new TranscriptionProperties("", values.uri(), values.model(), values.timeout()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	static Stream<TranscriptionArguments> invalidTranscriptionProperties() {
		return Stream.of(new TranscriptionArguments(null, "model", Duration.ofSeconds(1)),
				new TranscriptionArguments(URI.create("file:/tmp"), "model", Duration.ofSeconds(1)),
				new TranscriptionArguments(URI.create("ftp://example.com"), "model", Duration.ofSeconds(1)),
				new TranscriptionArguments(URI.create("https://example.com"), null, Duration.ofSeconds(1)),
				new TranscriptionArguments(URI.create("https://example.com"), " ", Duration.ofSeconds(1)),
				new TranscriptionArguments(URI.create("https://example.com"), "model", null),
				new TranscriptionArguments(URI.create("https://example.com"), "model", Duration.ZERO),
				new TranscriptionArguments(URI.create("https://example.com"), "model", Duration.ofSeconds(-1)));
	}

	@Test
	void rejectsInvalidWorkerProperties() {
		assertThatThrownBy(() -> new IngestionWorkerProperties(0, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new IngestionWorkerProperties(1, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	record VideoPropertiesArguments(Path storage, DataSize size, Duration maximum, Duration segment, Path ffmpeg,
			Path ffprobe) {
	}

	record TranscriptionArguments(URI uri, String model, Duration timeout) {
	}
}
