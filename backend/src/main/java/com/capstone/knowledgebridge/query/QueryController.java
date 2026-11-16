package com.capstone.knowledgebridge.query;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import tools.jackson.databind.JsonNode;

import com.capstone.knowledgebridge.cache.QueryMode;
import com.capstone.knowledgebridge.common.ApiError;
import com.capstone.knowledgebridge.gbrain.GbrainEvaluationService;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.usage.UsageService;
import com.capstone.knowledgebridge.query.QueryService.SearchResponse;
import com.capstone.knowledgebridge.query.QueryService.SynthesisResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/query")
public class QueryController {
	private static final Logger log = LoggerFactory.getLogger(QueryController.class);

	public record SearchRequest(@NotBlank @Size(max = 1000) String question, @Min(1) @Max(50) Integer limit) {
	}

	public record SynthesisRequest(@NotBlank @Size(max = 2000) String question) {
	}

	public record EvaluationRequest(@NotBlank @Size(max = 2000) String question,
			@NotBlank @Size(max = 255) String model,
			@NotNull @Size(max = 100) List<@NotNull @Min(1) Long> allowedItemIds) {
	}

	public record EvaluationResponse(SynthesisResponse synthesis, JsonNode trace) {
	}

	private final QueryService queryService;
	private final UsageService usageService;
	private final GbrainEvaluationService evaluationService;

	public QueryController(QueryService queryService, UsageService usageService,
			GbrainEvaluationService evaluationService) {
		this.queryService = queryService;
		this.usageService = usageService;
		this.evaluationService = evaluationService;
	}

	@PostMapping("/search")
	@PreAuthorize("isAuthenticated()")
	public SearchResponse search(@Valid @RequestBody SearchRequest request, @AuthenticationPrincipal Jwt jwt) {
		SearchResponse response = queryService.search(request.question(), request.limit());
		usageService.record(Long.parseLong(jwt.getSubject()), QueryMode.SEARCH, false, null);
		return response;
	}

	@PostMapping("/synthesize")
	@PreAuthorize("isAuthenticated()")
	public SynthesisResponse synthesize(@Valid @RequestBody SynthesisRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		SynthesisResponse response = queryService.synthesize(request.question());
		usageService.record(Long.parseLong(jwt.getSubject()), QueryMode.SYNTHESIS, response.cacheHit(),
				response.usage());
		return response;
	}

	// Evaluation is opt-in and admin-only; it bypasses caches so model/scope arms cannot share an old answer.
	@PostMapping("/evaluate")
	@PreAuthorize("hasRole('ADMIN')")
	public EvaluationResponse evaluate(@Valid @RequestBody EvaluationRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		var result = evaluationService.evaluate(request.question(), request.model(), request.allowedItemIds());
		var response = queryService.toSynthesisResponse(request.question(), result.synthesis());
		usageService.record(Long.parseLong(jwt.getSubject()), QueryMode.SYNTHESIS, false, response.usage());
		return new EvaluationResponse(response, result.trace());
	}

	@ExceptionHandler(GbrainException.class)
	ResponseEntity<ApiError> handleGbrainException(GbrainException exception, HttpServletRequest request) {
		Object mapping = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		String operation = mapping instanceof String pattern ? pattern : "/api/query";
		String action = switch (exception.code()) {
			case UNAUTHORIZED -> "Check backend gbrain OAuth credentials and scopes";
			case CONFIGURATION -> "Check backend gbrain adapter environment settings";
			case PROTOCOL -> "Check gbrain endpoint and protocol compatibility";
			case ENGINE -> "Check gbrain engine health and provider configuration";
			case NOT_FOUND -> "Check gbrain document indexing and availability";
			case RATE_LIMITED -> "Check gbrain and provider quota before retrying";
			case TIMEOUT, UNAVAILABLE -> "Check connectivity and gbrain service health";
			case VALIDATION -> "Check query validation and adapter input rules";
		};
		// Do not log the exception, cause, raw URI, or upstream text: they can contain credentials or content.
		log.warn("Knowledge service request failed: operation={}, category={}, action={}", operation, exception.code(),
				action);
		HttpStatus status = switch (exception.code()) {
			case VALIDATION -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.BAD_GATEWAY;
			case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
			case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
			case CONFIGURATION, UNAVAILABLE, PROTOCOL, ENGINE, NOT_FOUND -> HttpStatus.SERVICE_UNAVAILABLE;
		};
		String message = switch (exception.code()) {
			case VALIDATION -> "The query is invalid.";
			case RATE_LIMITED -> "The knowledge service is busy. Please try again shortly.";
			case TIMEOUT, UNAVAILABLE -> "The knowledge service is unavailable. Please try again.";
			case CONFIGURATION, UNAUTHORIZED, PROTOCOL, ENGINE, NOT_FOUND ->
				"The knowledge service could not complete the request.";
		};
		return ResponseEntity.status(status).body(new ApiError(exception.code().name(), message));
	}

}
