package com.capstone.knowledgebridge.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.mockito.MockedStatic;

class VideoStorageServiceTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void validatesNamesAndSizes() {
		VideoStorageService service = service(temporaryDirectory, 3);

		assertThat(service.isSupportedFilename(null)).isFalse();
		assertThat(service.isSupportedFilename("meeting.MP4")).isTrue();
		assertThat(service.isSupportedFilename("meeting.mov")).isTrue();
		assertThat(service.isSupportedFilename("meeting.webm")).isTrue();
		assertThat(service.isSupportedFilename("meeting.txt")).isFalse();
		assertThat(service.exceedsUploadLimit(3)).isFalse();
		assertThat(service.exceedsUploadLimit(4)).isTrue();
	}

	@Test
	void storesHashesAndDeletesAnUpload() throws Exception {
		VideoStorageService service = service(temporaryDirectory, 100);
		MockMultipartFile file = new MockMultipartFile("file", "meeting.mp4", "video/mp4",
				"abc".getBytes(StandardCharsets.UTF_8));

		StoredVideo stored = service.store(file);

		assertThat(stored.originalFilename()).isEqualTo("meeting.mp4");
		assertThat(stored.contentType()).isEqualTo("video/mp4");
		assertThat(stored.sha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
		assertThat(Files.readString(stored.path())).isEqualTo("abc");
		service.delete(stored.path());
		service.delete(stored.path());
		assertThat(stored.path()).doesNotExist();
	}

	@Test
	void reportsStorageAndCleanupFailures() throws Exception {
		Path fileInsteadOfDirectory = temporaryDirectory.resolve("file");
		Files.writeString(fileInsteadOfDirectory, "x");
		VideoStorageService service = service(fileInsteadOfDirectory, 100);
		MockMultipartFile upload = new MockMultipartFile("file", "meeting.mp4", "video/mp4", new byte[]{1});

		assertThatThrownBy(() -> service.store(upload)).isInstanceOf(VideoIngestionException.class)
				.hasMessage("Could not persist the uploaded video.");
		assertThatThrownBy(() -> service.delete(temporaryDirectory)).isInstanceOf(VideoIngestionException.class)
				.hasMessage("Could not remove the source video.");
	}

	@Test
	void reportsUnavailableSha256Algorithm() throws Exception {
		VideoStorageService service = service(temporaryDirectory, 100);
		MockMultipartFile upload = new MockMultipartFile("file", "meeting.mp4", "video/mp4", new byte[]{1});
		try (MockedStatic<MessageDigest> digests = mockStatic(MessageDigest.class, CALLS_REAL_METHODS)) {
			digests.when(() -> MessageDigest.getInstance("SHA-256"))
					.thenThrow(new NoSuchAlgorithmException("missing"));
			assertThatThrownBy(() -> service.store(upload)).isInstanceOf(IllegalStateException.class)
					.hasMessage("SHA-256 is unavailable");
		}
	}

	@Test
	void cleansOnlyDerivedAudioDirectories() throws Exception {
		Path derived = Files.createDirectory(temporaryDirectory.resolve("audio-orphan"));
		Files.writeString(derived.resolve("segment.mp3"), "audio");
		Path retained = Files.createDirectory(temporaryDirectory.resolve("retained"));
		Path source = Files.writeString(temporaryDirectory.resolve("source.mp4"), "video");

		service(temporaryDirectory, 100).cleanupDerivedAudio();

		assertThat(derived).doesNotExist();
		assertThat(retained).exists();
		assertThat(source).exists();
	}

	@Test
	void reportsDerivedAudioScanFailure() throws Exception {
		Path file = Files.writeString(temporaryDirectory.resolve("not-a-directory"), "x");

		assertThatThrownBy(() -> service(file, 100).cleanupDerivedAudio()).isInstanceOf(VideoIngestionException.class)
				.hasMessage("Could not scan for derived audio.");
	}

	private static VideoStorageService service(Path directory, long bytes) {
		return new VideoStorageService(new VideoProperties(directory, DataSize.ofBytes(bytes), Duration.ofHours(2),
				Duration.ofMinutes(20), Path.of("ffmpeg"), Path.of("ffprobe")));
	}
}
