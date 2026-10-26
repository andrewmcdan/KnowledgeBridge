package com.capstone.knowledgebridge.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.capstone.knowledgebridge.cache.QueryMode;

@Service
public class UsageService {

	private static final Logger log = LoggerFactory.getLogger(UsageService.class);
	private final JdbcTemplate jdbc;

	public record Totals(String scope, long queryCount, long inputTokens, long outputTokens, long totalTokens,
			long cacheHits, long estimatedQueryCount) {
	}

	public UsageService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void record(long userId, QueryMode mode, boolean cacheHit, TokenUsage usage) {
		long input = usage == null || usage.inputTokens() == null ? 0 : usage.inputTokens();
		long output = usage == null || usage.outputTokens() == null ? 0 : usage.outputTokens();
		boolean estimated = usage != null && usage.estimated();
		try {
			jdbc.update("""
					INSERT INTO query_usage (user_id, query_mode, cache_hit, input_tokens, output_tokens, estimated)
					VALUES (?, ?, ?, ?, ?, ?)
					""", userId, mode.name(), cacheHit, input, output, estimated);
		} catch (DataAccessException exception) {
			// Accounting must not turn an already completed provider request into an error that invites another paid
			// call.
			log.warn("Could not record query usage for user {}", userId, exception);
		}
	}

	public Totals totals(long userId, boolean admin) {
		String sql = """
				SELECT count(*) AS queries, coalesce(sum(input_tokens), 0) AS input,
				    coalesce(sum(output_tokens), 0) AS output, count(*) FILTER (WHERE cache_hit) AS hits,
				    count(*) FILTER (WHERE estimated) AS estimates FROM query_usage
				""";
		return jdbc.queryForObject(sql + (admin ? "" : " WHERE user_id = ?"), (row, index) -> {
			long input = row.getLong("input");
			long output = row.getLong("output");
			return new Totals(admin ? "workspace" : "personal", row.getLong("queries"), input, output, input + output,
					row.getLong("hits"), row.getLong("estimates"));
		}, admin ? new Object[0] : new Object[]{userId});
	}
}
