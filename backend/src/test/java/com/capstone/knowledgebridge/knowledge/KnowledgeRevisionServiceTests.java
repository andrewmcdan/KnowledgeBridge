package com.capstone.knowledgebridge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeRevisionServiceTests {

	@Test
	void bumpReturnsTheUpdatedRevision() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.queryForObject(
				"UPDATE system_state SET knowledge_revision = knowledge_revision + 1 WHERE id = 1 RETURNING knowledge_revision",
				Long.class)).thenReturn(5L);

		long revision = new KnowledgeRevisionService(jdbcTemplate).bump();

		assertThat(revision).isEqualTo(5L);
	}

}
