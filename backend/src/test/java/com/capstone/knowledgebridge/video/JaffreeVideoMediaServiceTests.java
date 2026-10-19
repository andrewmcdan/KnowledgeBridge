package com.capstone.knowledgebridge.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.util.unit.DataSize;

import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffprobe.FFprobe;
import com.github.kokorin.jaffree.ffprobe.FFprobeResult;
import com.github.kokorin.jaffree.ffprobe.Format;
import com.github.kokorin.jaffree.ffprobe.Stream;

class JaffreeVideoMediaServiceTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void extractsSortedSegmentsAndCleansThem() throws Exception {
		Stream videoStream = mock(Stream.class);
		when(videoStream.getCodecType()).thenReturn(StreamType.VIDEO);
		try (JaffreeMocks ignored = mocks(List.of(videoStream, audioStream()), 10.5f, true, null)) {
			ExtractedAudio audio = service(temporaryDirectory, Duration.ofHours(2)).extract(Path.of("video.mp4"));

			assertThat(audio.durationMs()).isEqualTo(10_500);
			assertThat(audio.segments()).extracting(path -> path.getFileName().toString())
					.containsExactly("segment-000.mp3", "segment-001.mp3");
			Path directory = audio.directory();
			audio.close();
			assertThat(directory).doesNotExist();
		}
	}

	@Test
	void rejectsMissingAudioUnknownAndNonPositiveDurationsAndLongVideos() {
		assertCode(List.of(), 1f, Duration.ofSeconds(2), "VIDEO_HAS_NO_AUDIO");
		assertCode(List.of(audioStream()), null, Duration.ofSeconds(2), "VIDEO_DURATION_INVALID");
		assertCode(List.of(audioStream()), 0f, Duration.ofSeconds(2), "VIDEO_DURATION_INVALID");
		assertCode(List.of(audioStream()), 3f, Duration.ofSeconds(2), "VIDEO_TOO_LONG");
	}

	@Test
	void rejectsEmptyExtractionOutputAndCleansTemporaryDirectory() {
		try (JaffreeMocks ignored = mocks(List.of(audioStream()), 1f, false, null)) {
			assertThatThrownBy(() -> service(temporaryDirectory, Duration.ofSeconds(2)).extract(Path.of("video.mp4")))
					.isInstanceOfSatisfying(VideoIngestionException.class,
							exception -> assertThat(exception.getCode()).isEqualTo("AUDIO_EXTRACTION_FAILED"));
			assertThat(temporaryDirectory).isEmptyDirectory();
		}
	}

	@Test
	void wrapsProbeExtractionAndFilesystemFailures() throws Exception {
		try (JaffreeMocks ignored = mocks(List.of(audioStream()), 1f, false, new IllegalStateException("probe"))) {
			assertExtractionFailed(service(temporaryDirectory, Duration.ofSeconds(2)));
		}
		try (JaffreeMocks ignored = mocks(List.of(audioStream()), 1f, false,
				new IllegalArgumentException("ffmpeg"))) {
			assertExtractionFailed(service(temporaryDirectory, Duration.ofSeconds(2)));
			assertThat(temporaryDirectory).isEmptyDirectory();
		}
		Path file = Files.writeString(temporaryDirectory.resolve("not-a-directory"), "x");
		try (JaffreeMocks ignored = mocks(List.of(audioStream()), 1f, false, null)) {
			assertExtractionFailed(service(file, Duration.ofSeconds(2)));
		}
	}

	@Test
	void extractedAudioReportsWalkAndDeleteFailures() throws Exception {
		Path missing = temporaryDirectory.resolve("missing");
		assertThatThrownBy(() -> new ExtractedAudio(0, missing, List.of()).close())
				.isInstanceOf(VideoIngestionException.class);

		Path directory = Files.createDirectory(temporaryDirectory.resolve("audio"));
		Path file = Files.writeString(directory.resolve("file"), "x");
		try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
			files.when(() -> Files.walk(directory)).thenReturn(java.util.stream.Stream.of(file));
			files.when(() -> Files.deleteIfExists(file)).thenThrow(new IOException("locked"));
			assertThatThrownBy(() -> new ExtractedAudio(0, directory, List.of(file)).close())
					.isInstanceOf(VideoIngestionException.class);
		}
	}

	private void assertCode(List<Stream> streams, Float duration, Duration maximum, String code) {
		try (JaffreeMocks ignored = mocks(streams, duration, false, null)) {
			assertThatThrownBy(() -> service(temporaryDirectory, maximum).extract(Path.of("video.mp4")))
					.isInstanceOfSatisfying(VideoIngestionException.class,
							exception -> assertThat(exception.getCode()).isEqualTo(code));
		}
	}

	private static void assertExtractionFailed(JaffreeVideoMediaService service) {
		assertThatThrownBy(() -> service.extract(Path.of("video.mp4")))
				.isInstanceOfSatisfying(VideoIngestionException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("AUDIO_EXTRACTION_FAILED"));
	}

	private static Stream audioStream() {
		Stream stream = mock(Stream.class);
		when(stream.getCodecType()).thenReturn(StreamType.AUDIO);
		return stream;
	}

	private static JaffreeVideoMediaService service(Path storage, Duration maximum) {
		return new JaffreeVideoMediaService(new VideoProperties(storage, DataSize.ofMegabytes(500), maximum,
				Duration.ofSeconds(1), Path.of("ffmpeg"), Path.of("ffprobe")));
	}

	private JaffreeMocks mocks(List<Stream> streams, Float duration, boolean createSegments,
			RuntimeException probeFailure) {
		FFprobeResult result = mock(FFprobeResult.class);
		Format format = mock(Format.class);
		when(result.getStreams()).thenReturn(streams);
		when(result.getFormat()).thenReturn(format);
		when(format.getDuration()).thenReturn(duration);
		MockedConstruction<FFprobe> probes = mockConstruction(FFprobe.class, (probe, context) -> {
			when(probe.setShowStreams(true)).thenReturn(probe);
			when(probe.setShowFormat(true)).thenReturn(probe);
			when(probe.setInput(any(Path.class))).thenReturn(probe);
			if (probeFailure == null || "ffmpeg".equals(probeFailure.getMessage())) {
				when(probe.execute()).thenReturn(result);
			} else {
				when(probe.execute()).thenThrow(probeFailure);
			}
		});
		MockedConstruction<FFmpeg> encoders = mockConstruction(FFmpeg.class, (encoder, context) -> {
			when(encoder.setOverwriteOutput(true)).thenReturn(encoder);
			when(encoder.addInput(any())).thenReturn(encoder);
			when(encoder.addOutput(any())).thenReturn(encoder);
			when(encoder.execute()).thenAnswer(invocation -> {
				if (probeFailure != null && "ffmpeg".equals(probeFailure.getMessage())) {
					throw probeFailure;
				}
				if (createSegments) {
					Path directory;
					try (var paths = Files.list(temporaryDirectory)) {
						directory = paths.filter(Files::isDirectory).findFirst().orElseThrow();
					}
					Files.writeString(directory.resolve("segment-001.mp3"), "two");
					Files.writeString(directory.resolve("segment-000.mp3"), "one");
				}
				return null;
			});
		});
		return new JaffreeMocks(probes, encoders);
	}

	private record JaffreeMocks(MockedConstruction<FFprobe> probes, MockedConstruction<FFmpeg> encoders)
			implements
				AutoCloseable {

		@Override
		public void close() {
			encoders.close();
			probes.close();
		}
	}
}
