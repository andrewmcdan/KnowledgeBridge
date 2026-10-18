package com.capstone.knowledgebridge.video;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("knowledgebridge.ingestion")
public record IngestionWorkerProperties(int workerCount, int queueCapacity) {

	public IngestionWorkerProperties {
		if (workerCount < 1 || queueCapacity < 1) {
			throw new IllegalArgumentException("Ingestion worker count and queue capacity must be positive");
		}
	}
}
