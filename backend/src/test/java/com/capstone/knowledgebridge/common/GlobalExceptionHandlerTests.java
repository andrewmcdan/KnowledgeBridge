package com.capstone.knowledgebridge.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTests {

	@Test
	void mapsOversizedUploadTo413() {
		var response = new GlobalExceptionHandler()
				.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(5_000_000));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
	}

}
