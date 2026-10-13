package com.capstone.knowledgebridge.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.query.QueryController.ErrorResponse;
import com.capstone.knowledgebridge.query.QueryController.SearchRequest;
import com.capstone.knowledgebridge.query.QueryController.SynthesisRequest;
import com.capstone.knowledgebridge.query.QueryService.RetrievalResponse;
import com.capstone.knowledgebridge.query.QueryService.SearchResponse;
import com.capstone.knowledgebridge.query.QueryService.SynthesisResponse;

class QueryControllerTests {

	private final QueryService queryService = mock(QueryService.class);

	private final QueryController controller = new QueryController(queryService);

	@Test
	void searchDelegatesToTheService() {
		SearchResponse expected = new SearchResponse(List.of(), new RetrievalResponse(true, true, false));
		when(queryService.search("What changed?", 5)).thenReturn(expected);

		SearchResponse response = controller.search(new SearchRequest("What changed?", 5));

		assertThat(response).isSameAs(expected);
		verify(queryService).search("What changed?", 5);
	}

	@Test
	void synthesizeDelegatesToTheService() {
		SynthesisResponse expected = new SynthesisResponse("Answer", true, false, List.of(), List.of(), List.of(),
				null);
		when(queryService.synthesize("What changed?")).thenReturn(expected);

		SynthesisResponse response = controller.synthesize(new SynthesisRequest("What changed?"));

		assertThat(response).isSameAs(expected);
		verify(queryService).synthesize("What changed?");
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
		ResponseEntity<ErrorResponse> response = controller
				.handleGbrainException(new GbrainException(code, "internal"));

		assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
		assertThat(response.getBody()).isEqualTo(new ErrorResponse(code.name(), expectedMessage));
	}

}
