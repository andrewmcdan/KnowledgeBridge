package com.capstone.knowledgebridge.usage;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

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

	public record DailyUsage(LocalDate date, long queryCount, long inputTokens, long outputTokens, long totalTokens,
			long cacheHits, long estimatedQueryCount, BigDecimal estimatedCostUsd, boolean hasEstimatedCost) {
	}

	public record ModelUsage(String model, long queryCount, long inputTokens, long outputTokens, long totalTokens,
			BigDecimal estimatedCostUsd, boolean hasEstimatedCost) {
	}

	public record Trends(String scope, LocalDate startDate, LocalDate endDate, List<DailyUsage> daily,
			List<ModelUsage> models, BigDecimal estimatedCostUsd, boolean hasEstimatedCost) {
	}

	public UsageService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void record(long userId, QueryMode mode, boolean cacheHit, TokenUsage usage) {
		long input = usage == null || usage.inputTokens() == null ? 0 : usage.inputTokens();
		long output = usage == null || usage.outputTokens() == null ? 0 : usage.outputTokens();
		boolean estimated = usage != null && usage.estimated();
		String model = usage == null || usage.model() == null || usage.model().isBlank() ? null : usage.model();
		BigDecimal cost = usage == null || usage.estimatedCostUsd() == null
				|| usage.estimatedCostUsd().signum() < 0 ? null : usage.estimatedCostUsd();
		try {
			jdbc.update(
					"""
							INSERT INTO query_usage
							    (user_id, query_mode, cache_hit, input_tokens, output_tokens, estimated, provider_model, estimated_cost_usd)
							VALUES (?, ?, ?, ?, ?, ?, ?, ?)
							""",
					userId, mode.name(), cacheHit, input, output, estimated, model, cost);
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

	public Trends trends(long userId, boolean admin) {
		LocalDate endDate = LocalDate.now(ZoneOffset.UTC);
		LocalDate startDate = endDate.minusDays(29);
		Timestamp start = Timestamp.from(startDate.atStartOfDay().toInstant(ZoneOffset.UTC));
		Timestamp endExclusive = Timestamp.from(endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
		List<DailyUsage> daily = dailyUsage(userId, admin, startDate, endDate, start, endExclusive);
		List<ModelUsage> models = modelUsage(userId, admin, start, endExclusive);
		BigDecimal estimatedCostUsd = daily.stream().map(DailyUsage::estimatedCostUsd).reduce(BigDecimal.ZERO,
				BigDecimal::add);
		boolean hasEstimatedCost = daily.stream().anyMatch(DailyUsage::hasEstimatedCost);
		return new Trends(admin ? "workspace" : "personal", startDate, endDate, daily, models, estimatedCostUsd,
				hasEstimatedCost);
	}

	private List<DailyUsage> dailyUsage(long userId, boolean admin, LocalDate startDate, LocalDate endDate,
			Timestamp start, Timestamp endExclusive) {
		String scope = admin ? "" : " AND user_id = ?";
		String sql = """
				WITH days AS (
				    SELECT value::date AS day
				    FROM generate_series(CAST(? AS date), CAST(? AS date), INTERVAL '1 day') AS generated(value)
				), usage_by_day AS (
				    SELECT (created_at AT TIME ZONE 'UTC')::date AS day, count(*) AS queries,
				        coalesce(sum(input_tokens), 0) AS input, coalesce(sum(output_tokens), 0) AS output,
				        count(*) FILTER (WHERE cache_hit) AS hits, count(*) FILTER (WHERE estimated) AS estimates,
				        coalesce(sum(estimated_cost_usd), 0) AS cost, count(estimated_cost_usd) > 0 AS has_cost
				    FROM query_usage
				    WHERE created_at >= ? AND created_at < ?%s
				    GROUP BY (created_at AT TIME ZONE 'UTC')::date
				)
				SELECT days.day, coalesce(usage_by_day.queries, 0) AS queries, coalesce(usage_by_day.input, 0) AS input,
				    coalesce(usage_by_day.output, 0) AS output, coalesce(usage_by_day.hits, 0) AS hits,
				    coalesce(usage_by_day.estimates, 0) AS estimates, coalesce(usage_by_day.cost, 0) AS cost,
				    coalesce(usage_by_day.has_cost, false) AS has_cost
				FROM days LEFT JOIN usage_by_day ON usage_by_day.day = days.day
				ORDER BY days.day
				""".formatted(scope);
		return jdbc.query(sql, (row, index) -> {
			long input = row.getLong("input");
			long output = row.getLong("output");
			return new DailyUsage(row.getObject("day", LocalDate.class), row.getLong("queries"), input, output,
					input + output, row.getLong("hits"), row.getLong("estimates"), row.getBigDecimal("cost"),
					row.getBoolean("has_cost"));
		}, scopedArgs(userId, admin, Date.valueOf(startDate), Date.valueOf(endDate), start, endExclusive));
	}

	private List<ModelUsage> modelUsage(long userId, boolean admin, Timestamp start, Timestamp endExclusive) {
		String scope = admin ? "" : " AND user_id = ?";
		String sql = """
				SELECT provider_model AS model, count(*) AS queries, coalesce(sum(input_tokens), 0) AS input,
				    coalesce(sum(output_tokens), 0) AS output, coalesce(sum(estimated_cost_usd), 0) AS cost,
				    count(estimated_cost_usd) > 0 AS has_cost
				FROM query_usage
				WHERE created_at >= ? AND created_at < ? AND provider_model IS NOT NULL AND provider_model <> ''%s
				GROUP BY provider_model
				ORDER BY sum(input_tokens) + sum(output_tokens) DESC, provider_model
				""".formatted(scope);
		return jdbc.query(sql, (row, index) -> {
			long input = row.getLong("input");
			long output = row.getLong("output");
			return new ModelUsage(row.getString("model"), row.getLong("queries"), input, output, input + output,
					row.getBigDecimal("cost"), row.getBoolean("has_cost"));
		}, scopedArgs(userId, admin, start, endExclusive));
	}

	private Object[] scopedArgs(long userId, boolean admin, Object... args) {
		List<Object> values = new ArrayList<>(List.of(args));
		if (!admin)
			values.add(userId);
		return values.toArray();
	}
}
