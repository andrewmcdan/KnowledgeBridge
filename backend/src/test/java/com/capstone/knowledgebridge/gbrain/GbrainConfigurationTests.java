package com.capstone.knowledgebridge.gbrain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

import com.capstone.knowledgebridge.gbrain.model.GbrainHealth;

import tools.jackson.databind.json.JsonMapper;

class GbrainConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withBean(JsonMapper.class, () -> JsonMapper.builder().build())
			.withUserConfiguration(GbrainConfiguration.class);

	@Test
	void bindsPropertiesAndCreatesTransportBeansWithoutContactingGbrain() {
		runner.withPropertyValues(
				"knowledgebridge.gbrain.enabled=false",
				"knowledgebridge.gbrain.base-url=http://gbrain:3131",
				"knowledgebridge.gbrain.oauth-token-url=http://gbrain:3131/token",
				"knowledgebridge.gbrain.connect-timeout=PT2S",
				"knowledgebridge.gbrain.read-timeout=PT30S",
				"knowledgebridge.gbrain.synthesis-timeout=PT120S",
				"knowledgebridge.gbrain.max-response-size=4MB",
				"knowledgebridge.gbrain.retry-max-attempts=3",
				"knowledgebridge.gbrain.retry-max-backoff=PT5S")
				.run(context -> {
					assertThat(context).hasNotFailed();
					GbrainProperties properties = context.getBean(GbrainProperties.class);
					assertThat(properties.synthesisTimeout()).isEqualTo(Duration.ofMinutes(2));
					assertThat(properties.maxResponseSize()).isEqualTo(DataSize.ofMegabytes(4));
					assertThat(properties.hasCredentials()).isFalse();
					assertThat(context).hasSingleBean(GbrainMcpClient.class);
					assertThat(context).hasSingleBean(GbrainTokenProvider.class);
					assertThat(context.getBean(GbrainClient.class)).isInstanceOf(McpGbrainClient.class);
					assertThat(context.getBean(GbrainHealthClient.class).check()).isEqualTo(GbrainHealth.disabled());
				});
	}

	@Test
	void inMemoryModeReplacesGbrainForLocalDevelopment() {
		runner.withPropertyValues("knowledgebridge.gbrain.mode=in-memory",
				"knowledgebridge.gbrain.base-url=http://gbrain:3131",
				"knowledgebridge.gbrain.oauth-token-url=http://gbrain:3131/token",
				"knowledgebridge.gbrain.connect-timeout=PT2S",
				"knowledgebridge.gbrain.read-timeout=PT30S",
				"knowledgebridge.gbrain.synthesis-timeout=PT120S",
				"knowledgebridge.gbrain.max-response-size=4MB",
				"knowledgebridge.gbrain.retry-max-attempts=3",
				"knowledgebridge.gbrain.retry-max-backoff=PT5S")
				.run(context -> {
					assertThat(context.getBean(GbrainProperties.class).mode())
							.isEqualTo(GbrainProperties.Mode.IN_MEMORY);
					assertThat(context.getBean(GbrainClient.class)).isInstanceOf(InMemoryGbrainClient.class);
				});
	}

	@Test
	void rejectsInvalidConfigurationAtStartup() {
		runner.withPropertyValues("knowledgebridge.gbrain.base-url=ftp://gbrain")
				.run(context -> assertThat(context).hasFailed());
	}

}
