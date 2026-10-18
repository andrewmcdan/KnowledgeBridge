package com.capstone.knowledgebridge.video;

import java.net.http.HttpClient;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
@EnableConfigurationProperties({VideoProperties.class, TranscriptionProperties.class,
		IngestionWorkerProperties.class})
public class VideoConfiguration {

	@Bean("videoIngestionExecutor")
	TaskExecutor videoIngestionExecutor(IngestionWorkerProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.workerCount());
		executor.setMaxPoolSize(properties.workerCount());
		executor.setQueueCapacity(properties.queueCapacity());
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.setThreadNamePrefix("video-ingestion-");
		executor.initialize();
		return executor;
	}

	@Bean
	RestClient transcriptionRestClient(TranscriptionProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.timeout());
		return RestClient.builder().baseUrl(properties.baseUrl().toString()).requestFactory(requestFactory).build();
	}
}
