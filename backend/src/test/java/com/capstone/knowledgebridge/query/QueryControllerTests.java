package com.capstone.knowledgebridge.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.common.ApiError;
import com.capstone.knowledgebridge.cache.QueryMode;
import com.capstone.knowledgebridge.usage.UsageService;
import com.capstone.knowledgebridge.query.QueryController.SearchRequest;
import com.capstone.knowledgebridge.query.QueryController.SynthesisRequest;
import com.capstone.knowledgebridge.query.QueryService.RetrievalResponse;
import com.capstone.knowledgebridge.query.QueryService.SearchResponse;
import com.capstone.knowledgebridge.query.QueryService.SynthesisResponse;

class QueryControllerTests {

	private final QueryService queryService = mock(QueryService.class);

	private final UsageService usage = mock(UsageService.class);
	private final QueryController controller = new QueryController(queryService, usage);

	private Jwt user() {
		Jwt jwt = mock(Jwt.class);
		when(jwt.getSubject()).thenReturn("2");
		return jwt;
	}

	@Test
	void searchDelegatesToTheService() {
		SearchResponse expected = new SearchResponse(List.of(), new RetrievalResponse(true, true, false));
		when(queryService.search("What changed?", 5)).thenReturn(expected);

		SearchResponse response = controller.search(new SearchRequest("What changed?", 5), user());

		assertThat(response).isSameAs(expected);
		verify(queryService).search("What changed?", 5);
		verify(usage).record(2L, QueryMode.SEARCH, false, null);
	}

	@Test
	void synthesizeDelegatesToTheService() {
		SynthesisResponse expected = new SynthesisResponse("Answer", true, false, List.of(), List.of(), List.of(),
				null, false);
		when(queryService.synthesize("What changed?")).thenReturn(expected);

		SynthesisResponse response = controller.synthesize(new SynthesisRequest("What changed?"), user());

		assertThat(response).isSameAs(expected);
		verify(queryService).synthesize("What changed?");
		verify(usage).record(2L, QueryMode.SYNTHESIS, false, null);
	}

	@Test
	void gbrainErrorsMapToSafeApiResponses() {
		assertError(GbrainErrorCode.VALIDATION, HttpStatus.BAD_REQUEST, "The query is invalid.");
		assertError(GbrainErrorCode.UNAUTHORIZED, HttpStatus.BAD_GATEWAY,
				"The knowledge service could not complete the request.");
		assertError(GbrainErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
				"The knowledge service is busy. Please try again shortly.");
		assertError(GbrainErrorCode.TIMEOUT, HttpStatus.GATEWAY_TIMEOUT,
				"The knowledge service is unavailable. Please try again.");
		assertError(GbrainErrorCode.UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
				"The knowledge service is unavailable. Please try again.");
		assertError(GbrainErrorCode.CONFIGURATION, HttpStatus.SERVICE_UNAVAILABLE,
				"The knowledge service could not complete the request.");
		assertError(GbrainErrorCode.PROTOCOL, HttpStatus.SERVICE_UNAVAILABLE,
				"The knowledge service could not complete the request.");
		assertError(GbrainErrorCode.ENGINE, HttpStatus.SERVICE_UNAVAILABLE,
				"The knowledge service could not complete the request.");
		assertError(GbrainErrorCode.NOT_FOUND, HttpStatus.SERVICE_UNAVAILABLE,
				"The knowledge service could not complete the request.");
	}

	private void assertError(GbrainErrorCode code, HttpStatus expectedStatus, String expectedMessage) {
		ResponseEntity<ApiError> response = controller
				.handleGbrainException(new GbrainException(code, "internal"));

		assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
		assertThat(response.getBody()).isEqualTo(new ApiError(code.name(), expectedMessage));
	}

}
