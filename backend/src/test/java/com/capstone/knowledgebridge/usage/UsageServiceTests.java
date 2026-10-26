package com.capstone.knowledgebridge.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.capstone.knowledgebridge.cache.QueryMode;

class UsageServiceTests {

	@Test
	void tokenEstimatesRoundUpAndAllowEmptyText() {
		assertThat(TokenEstimator.estimate(0)).isZero();
		assertThat(TokenEstimator.estimate(1)).isEqualTo(1);
		assertThat(TokenEstimator.estimate(4)).isEqualTo(1);
		assertThat(TokenEstimator.estimate(5)).isEqualTo(2);
	}

	@Test
	void missingCountsAndAccountingFailureDoNotFailACompletedQuery() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.update(anyString(), any(Object[].class)))
				.thenThrow(new DataAccessResourceFailureException("offline"));
		UsageService usage = new UsageService(jdbc);
		assertThatNoException().isThrownBy(() -> usage.record(1, QueryMode.SEARCH, false, null));
		assertThatNoException().isThrownBy(() -> usage.record(1, QueryMode.SYNTHESIS, false,
				new TokenUsage("model", null, 1L, null, true)));
		assertThatNoException().isThrownBy(() -> usage.record(1, QueryMode.SYNTHESIS, false,
				new TokenUsage("model", 1L, null, null)));
	}
}
