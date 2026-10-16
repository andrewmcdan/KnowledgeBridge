package com.capstone.knowledgebridge.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.json.JsonMapper;

/**
 * Runs QueryCacheService against a real PostgreSQL migrated by the application's own Flyway scripts, so the md5 cache
 * key, ON CONFLICT, and hit counting are exercised exactly as V7 defines them. No Spring context is needed.
 */
@Testcontainers
class QueryCacheServiceTests {

	private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
			.parse("pgvector/pgvector:pg17")
			.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE);

	private static JdbcTemplate jdbcTemplate;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private QueryCacheService cache;

	/** Stand-in for SynthesisResponse: the cache only needs something JSON round-trippable. */
	record CachedAnswer(String answer, List<String> citations) {
	}

	@BeforeAll
	static void migrate() {
		Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.load()
				.migrate();
		jdbcTemplate = new JdbcTemplate(
				new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
	}

	@BeforeEach
	void emptyCache() {
		jdbcTemplate.update("TRUNCATE query_cache");
		cache = new QueryCacheService(jdbcTemplate, jsonMapper);
	}

	@Test
	void lookupMissesWhenNothingIsStored() {
		assertThat(cache.lookup("What is the PTO policy?", QueryMode.SYNTHESIS, 1, CachedAnswer.class)).isEmpty();
	}

	@Test
	void storedResponseIsReturnedAndEveryHitIsCounted() {
		CachedAnswer answer = new CachedAnswer("15 days", List.of("knowledgebridge/1"));
		cache.store("What is the PTO policy?", QueryMode.SYNTHESIS, 7, answer);

		assertThat(cache.lookup("What is the PTO policy?", QueryMode.SYNTHESIS, 7, CachedAnswer.class))
				.contains(answer);
		assertThat(cache.lookup("What is the PTO policy?", QueryMode.SYNTHESIS, 7, CachedAnswer.class))
				.contains(answer);

		Map<String, Object> row = jdbcTemplate.queryForMap(
				"SELECT hit_count, last_accessed_at >= created_at AS touched FROM query_cache");
		assertThat(row).containsEntry("hit_count", 2).containsEntry("touched", true);
	}

	@Test
	void matchingIgnoresSurroundingWhitespaceButOtherwiseIsExact() {
		CachedAnswer answer = new CachedAnswer("15 days", List.of());
		cache.store("  What is the PTO policy?\n", QueryMode.SYNTHESIS, 1, answer);

		assertThat(cache.lookup("What is the PTO policy?", QueryMode.SYNTHESIS, 1, CachedAnswer.class))
				.contains(answer);
		assertThat(cache.lookup("what is the pto policy?", QueryMode.SYNTHESIS, 1, CachedAnswer.class)).isEmpty();
		assertThat(jdbcTemplate.queryForObject("SELECT exact_query_text FROM query_cache", String.class))
				.isEqualTo("What is the PTO policy?");
	}

	@Test
	void revisionBumpMakesOlderEntriesStaleWithoutDeletingThem() {
		CachedAnswer before = new CachedAnswer("15 days", List.of());
		CachedAnswer after = new CachedAnswer("20 days", List.of());
		cache.store("What is the PTO policy?", QueryMode.SYNTHESIS, 7, before);

		// A knowledge write moved the revision to 8: the old answer no longer matches.
		assertThat(cache.lookup("What is the PTO policy?", QueryMode.SYNTHESIS, 8, CachedAnswer.class)).isEmpty();

		cache.store("What is the PTO policy?", QueryMode.SYNTHESIS, 8, after);
		assertThat(cache.lookup("What is the PTO policy?", QueryMode.SYNTHESIS, 8, CachedAnswer.class))
				.contains(after);
		assertThat(cache.lookup("What is the PTO policy?", QueryMode.SYNTHESIS, 7, CachedAnswer.class))
				.contains(before);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM query_cache", Integer.class)).isEqualTo(2);
	}

	@Test
	void modeIsPartOfTheKey() {
		cache.store("What is the PTO policy?", QueryMode.SYNTHESIS, 1, new CachedAnswer("15 days", List.of()));

		assertThat(cache.lookup("What is the PTO policy?", QueryMode.SEARCH, 1, CachedAnswer.class)).isEmpty();
	}

	@Test
	void storingTheSameKeyAgainKeepsTheFirstResponse() {
		CachedAnswer first = new CachedAnswer("15 days", List.of());
		cache.store("What is the PTO policy?", QueryMode.SYNTHESIS, 1, first);
		cache.store("What is the PTO policy?", QueryMode.SYNTHESIS, 1, new CachedAnswer("changed", List.of()));

		assertThat(cache.lookup("What is the PTO policy?", QueryMode.SYNTHESIS, 1, CachedAnswer.class))
				.contains(first);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM query_cache", Integer.class)).isEqualTo(1);
	}

	@Test
	void questionsBeyondTheBtreeEntryLimitAreCached() {
		// 2,000 four-byte characters is 8 KB, about three times the B-tree entry limit for an unhashed key.
		String longQuestion = "😀".repeat(2000);
		CachedAnswer answer = new CachedAnswer("long", List.of());
		cache.store(longQuestion, QueryMode.SYNTHESIS, 1, answer);

		assertThat(cache.lookup(longQuestion, QueryMode.SYNTHESIS, 1, CachedAnswer.class)).contains(answer);
	}

	@Test
	void lookupTreatsADatabaseFailureAsAMiss() {
		JdbcTemplate failingJdbc = mock(JdbcTemplate.class);
		when(failingJdbc.queryForList(anyString(), eq(String.class), any(Object[].class)))
				.thenThrow(new DataAccessResourceFailureException("database down"));

		Optional<CachedAnswer> result = new QueryCacheService(failingJdbc, jsonMapper)
				.lookup("What is the PTO policy?", QueryMode.SYNTHESIS, 1, CachedAnswer.class);

		assertThat(result).isEmpty();
	}

	@Test
	void lookupTreatsAnUnreadableEntryAsAMiss() {
		JdbcTemplate staleShapeJdbc = mock(JdbcTemplate.class);
		when(staleShapeJdbc.queryForList(anyString(), eq(String.class), any(Object[].class)))
				.thenReturn(List.of("not json"));

		Optional<CachedAnswer> result = new QueryCacheService(staleShapeJdbc, jsonMapper)
				.lookup("What is the PTO policy?", QueryMode.SYNTHESIS, 1, CachedAnswer.class);

		assertThat(result).isEmpty();
	}

	@Test
	void storeSwallowsADatabaseFailure() {
		JdbcTemplate failingJdbc = mock(JdbcTemplate.class);
		when(failingJdbc.update(anyString(), any(Object[].class)))
				.thenThrow(new DataAccessResourceFailureException("database down"));
		QueryCacheService failingCache = new QueryCacheService(failingJdbc, jsonMapper);

		assertThatNoException().isThrownBy(() -> failingCache.store("What is the PTO policy?", QueryMode.SYNTHESIS,
				1, new CachedAnswer("15 days", List.of())));
	}

}
