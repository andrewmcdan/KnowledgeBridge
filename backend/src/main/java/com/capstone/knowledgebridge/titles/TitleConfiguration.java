package com.capstone.knowledgebridge.titles;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TitleConfiguration {

	@Bean
	RestClient titleRestClient(@Value("${knowledgebridge.titles.base-url}") String baseUrl,
			@Value("${knowledgebridge.titles.timeout}") Duration timeout) {
		HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
		factory.setReadTimeout(timeout);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
	}
}
