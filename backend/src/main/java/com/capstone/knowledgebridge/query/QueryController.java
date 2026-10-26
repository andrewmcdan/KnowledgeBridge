package com.capstone.knowledgebridge.query;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.common.ApiError;
import com.capstone.knowledgebridge.query.QueryService.SearchResponse;
import com.capstone.knowledgebridge.query.QueryService.SynthesisResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/query")
public class QueryController {

	public record SearchRequest(@NotBlank @Size(max = 1000) String question, @Min(1) @Max(50) Integer limit) {
	}

	public record SynthesisRequest(@NotBlank @Size(max = 2000) String question) {
	}

	private final QueryService queryService;

	public QueryController(QueryService queryService) {
		this.queryService = queryService;
	}

	@PostMapping("/search")
	@PreAuthorize("isAuthenticated()")
	public SearchResponse search(@Valid @RequestBody SearchRequest request) {
		return queryService.search(request.question(), request.limit());
	}

	@PostMapping("/synthesize")
	@PreAuthorize("isAuthenticated()")
	public SynthesisResponse synthesize(@Valid @RequestBody SynthesisRequest request) {
		return queryService.synthesize(request.question());
	}

	@ExceptionHandler(GbrainException.class)
	ResponseEntity<ApiError> handleGbrainException(GbrainException exception) {
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
