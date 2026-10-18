package com.capstone.knowledgebridge.video;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OpenAiTranscriptionClient implements TranscriptionClient {

	private static final long OPENAI_FILE_LIMIT = 25L * 1024 * 1024;

	private final RestClient restClient;

	private final TranscriptionProperties properties;

	public OpenAiTranscriptionClient(RestClient transcriptionRestClient, TranscriptionProperties properties) {
		this.restClient = transcriptionRestClient;
		this.properties = properties;
	}

	@Override
	public String transcribe(Path audioSegment) {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new VideoIngestionException("TRANSCRIPTION_CONFIGURATION", "OpenAI transcription is not configured.");
		}
		try {
			if (Files.size(audioSegment) > OPENAI_FILE_LIMIT) {
				throw new VideoIngestionException("TRANSCRIPTION_FILE_TOO_LARGE",
						"An extracted audio segment exceeds OpenAI's file limit.");
			}
			LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
			body.add("file", new FileSystemResource(audioSegment));
			body.add("model", properties.model());
			Map<?, ?> response = restClient.post().uri("/audio/transcriptions")
					.header("Authorization", "Bearer " + properties.apiKey()).contentType(MediaType.MULTIPART_FORM_DATA)
					.body(body).retrieve().body(Map.class);
			Object text = response == null ? null : response.get("text");
			if (!(text instanceof String transcript) || transcript.isBlank()) {
				throw new VideoIngestionException("TRANSCRIPTION_RESPONSE_INVALID",
						"OpenAI returned an invalid transcription response.");
			}
			return transcript.trim();
		} catch (VideoIngestionException ex) {
			throw ex;
		} catch (RestClientResponseException ex) {
			String code = ex.getStatusCode().value() == 429
					? "TRANSCRIPTION_RATE_LIMITED"
					: "TRANSCRIPTION_REJECTED";
			throw new VideoIngestionException(code, "OpenAI rejected the transcription request.", ex);
		} catch (RestClientException | java.io.IOException ex) {
			throw new VideoIngestionException("TRANSCRIPTION_UNAVAILABLE", "OpenAI transcription is unavailable.", ex);
		}
	}

	@Override
	public String model() {
		return properties.model();
	}
}
