package com.capstone.knowledgebridge.video;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VideoStorageService {

	private static final Set<String> EXTENSIONS = Set.of(".mp4", ".mov", ".webm");

	private final VideoProperties properties;

	public VideoStorageService(VideoProperties properties) {
		this.properties = properties;
	}

	public boolean isSupportedFilename(String filename) {
		if (filename == null) {
			return false;
		}
		String lower = filename.toLowerCase(Locale.ROOT);
		return EXTENSIONS.stream().anyMatch(lower::endsWith);
	}

	public boolean exceedsUploadLimit(long size) {
		return size > properties.maxUploadSize().toBytes();
	}

	public StoredVideo store(MultipartFile file) {
		String originalFilename = file.getOriginalFilename();
		String extension = EXTENSIONS.stream()
				.filter(candidate -> originalFilename.toLowerCase(Locale.ROOT).endsWith(candidate))
				.findFirst()
				.orElseThrow();
		try {
			Files.createDirectories(properties.storageDirectory());
			Path destination = properties.storageDirectory().resolve(UUID.randomUUID() + extension).normalize();
			file.transferTo(destination);
			return new StoredVideo(originalFilename, file.getContentType(), sha256(destination), destination);
		} catch (IOException ex) {
			throw new VideoIngestionException("VIDEO_STORAGE_FAILED", "Could not persist the uploaded video.", ex);
		}
	}

	public void delete(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ex) {
			throw new VideoIngestionException("VIDEO_CLEANUP_FAILED", "Could not remove the source video.", ex);
		}
	}

	public void cleanupDerivedAudio() {
		try {
			Files.createDirectories(properties.storageDirectory());
			try (var paths = Files.list(properties.storageDirectory())) {
				paths.filter(Files::isDirectory)
						.filter(path -> path.getFileName().toString().startsWith("audio-"))
						.forEach(path -> new ExtractedAudio(0, path, List.of()).close());
			}
		} catch (IOException ex) {
			throw new VideoIngestionException("AUDIO_CLEANUP_FAILED", "Could not scan for derived audio.", ex);
		}
	}

	private static String sha256(Path path) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
				input.transferTo(OutputStream.nullOutputStream());
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}
}
