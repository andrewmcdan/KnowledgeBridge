package com.capstone.knowledgebridge.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTests {

	@Test
	void mapsOversizedUploadTo413() throws Exception {
		var response = new GlobalExceptionHandler()
				.handleException(new MaxUploadSizeExceededException(5_000_000),
						new ServletWebRequest(new MockHttpServletRequest()));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(response.getBody()).isEqualTo(ApiError.forStatus(413));
	}

}
