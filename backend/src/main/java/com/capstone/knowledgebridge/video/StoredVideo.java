package com.capstone.knowledgebridge.video;

import java.nio.file.Path;

public record StoredVideo(String originalFilename, String contentType, String sha256, Path path) {
}
