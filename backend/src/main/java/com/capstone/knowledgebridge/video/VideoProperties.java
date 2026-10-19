package com.capstone.knowledgebridge.video;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("knowledgebridge.video")
public record VideoProperties(Path storageDirectory, DataSize maxUploadSize, Duration maxDuration,
		Duration segmentDuration, Path ffmpegPath, Path ffprobePath) {

	public VideoProperties {
		if (storageDirectory == null || maxUploadSize == null || maxUploadSize.toBytes() <= 0 || maxDuration == null
				|| maxDuration.isNegative() || maxDuration.isZero() || segmentDuration == null
				|| segmentDuration.isNegative() || segmentDuration.isZero()
				|| segmentDuration.compareTo(maxDuration) > 0
				|| ffmpegPath == null || ffprobePath == null) {
			throw new IllegalArgumentException("Invalid knowledgebridge.video configuration");
		}
	}
}
