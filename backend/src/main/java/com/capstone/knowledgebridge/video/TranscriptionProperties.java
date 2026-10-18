package com.capstone.knowledgebridge.video;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("knowledgebridge.transcription")
public record TranscriptionProperties(String apiKey, URI baseUrl, String model, Duration timeout) {

	public TranscriptionProperties {
		if (baseUrl == null || baseUrl.getHost() == null || !("http".equals(baseUrl.getScheme())
				|| "https".equals(baseUrl.getScheme())) || model == null || model.isBlank() || timeout == null
				|| timeout.isNegative() || timeout.isZero()) {
			throw new IllegalArgumentException("Invalid knowledgebridge.transcription configuration");
		}
	}
}
