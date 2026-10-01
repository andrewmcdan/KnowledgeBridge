package com.capstone.knowledgebridge.gbrain;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the gbrain transport. No bean contacts gbrain during startup, so the application starts while gbrain is
 * unavailable or not yet provisioned; the first call or an explicit capability refresh reports the problem.
 */
@Configuration
@EnableConfigurationProperties(GbrainProperties.class)
public class GbrainConfiguration {

	@Bean
	GbrainTokenProvider gbrainTokenProvider(GbrainProperties properties, JsonMapper jsonMapper) {
		return new GbrainTokenProvider(properties, jsonMapper, Clock.systemUTC());
	}

	@Bean
	GbrainMcpClient gbrainMcpClient(GbrainProperties properties, GbrainTokenProvider tokenProvider,
			JsonMapper jsonMapper) {
		return new GbrainMcpClient(properties, tokenProvider, jsonMapper, Thread::sleep);
	}

	@Bean
	GbrainHealthClient gbrainHealthClient(GbrainProperties properties, JsonMapper jsonMapper) {
		return new GbrainHealthClient(properties, jsonMapper);
	}

}
