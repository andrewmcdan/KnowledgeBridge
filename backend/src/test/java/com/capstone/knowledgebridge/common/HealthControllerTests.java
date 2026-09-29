package com.capstone.knowledgebridge.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTests {

	@Test
	void reportsServiceUnavailableWhenDatabaseCheckFails() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		doThrow(new RuntimeException("database unavailable"))
				.when(jdbcTemplate).queryForObject("SELECT 1", Integer.class);

		var response = new HealthController(jdbcTemplate).health();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo("DOWN");
		assertThat(response.getBody().database()).isEqualTo("DOWN");
		assertThat(response.getBody().timestamp()).isNotNull();
	}
}
