package com.capstone.knowledgebridge.gbrain;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(GbrainConfiguration.class);

	@Bean
	GbrainTokenProvider gbrainTokenProvider(GbrainProperties properties, JsonMapper jsonMapper) {
		return new GbrainTokenProvider(properties, jsonMapper, Clock.systemUTC());
	}

	@Bean
	GbrainMcpClient gbrainMcpClient(GbrainProperties properties, GbrainTokenProvider tokenProvider,
			JsonMapper jsonMapper) {
		return new GbrainMcpClient(properties, tokenProvider, jsonMapper, Thread::sleep);
	}

	/** The single application-facing client; services depend on this interface, never on the transport. */
	@Bean
	GbrainClient gbrainClient(GbrainProperties properties, GbrainMcpClient transport) {
		if (properties.mode() == GbrainProperties.Mode.IN_MEMORY) {
			log.warn("gbrain mode is IN_MEMORY: knowledge items are not indexed and are lost on restart");
			return new InMemoryGbrainClient(Clock.systemUTC());
		}
		return new McpGbrainClient(transport);
	}

	@Bean
	GbrainHealthClient gbrainHealthClient(GbrainProperties properties, JsonMapper jsonMapper) {
		return new GbrainHealthClient(properties, jsonMapper);
	}

}
