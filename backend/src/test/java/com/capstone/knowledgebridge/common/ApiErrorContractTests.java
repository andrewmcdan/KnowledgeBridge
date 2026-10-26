package com.capstone.knowledgebridge.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

class ApiErrorContractTests {

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ErrorEndpoints())
			.setControllerAdvice(new GlobalExceptionHandler(), new ApiErrorResponseAdvice()).build();

	@Test
	void statusCodesHaveStableMachineReadableCodes() {
		Map<Integer, String> codes = Map.of(400, "VALIDATION_ERROR", 401, "UNAUTHENTICATED", 403, "FORBIDDEN",
				404, "NOT_FOUND", 405, "METHOD_NOT_ALLOWED", 409, "CONFLICT", 413, "CONTENT_TOO_LARGE",
				415, "UNSUPPORTED_MEDIA_TYPE", 500, "INTERNAL_ERROR", 418, "REQUEST_FAILED");
		codes.forEach((status, code) -> {
			assertThat(ApiError.forStatus(status).code()).isEqualTo(code);
			assertThat(ApiError.forStatus(status).message()).isNotBlank();
		});
	}

	@Test
	void emptyErrorsAndExistingErrorBodiesUseJsonWithoutChangingSuccessfulBodies() throws Exception {
		mvc.perform(get("/empty"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value("CONFLICT"));
		mvc.perform(get("/custom"))
				.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("UNAVAILABLE"));
		mvc.perform(get("/fallback"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.exception").doesNotExist());
		mvc.perform(get("/success"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.value").value("unchanged"));
	}

	@Test
	void validationMalformedJsonAndMissingRoutesShareTheEnvelope() throws Exception {
		mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(get("/validated"))
				.andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		mvc.perform(get("/missing"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void methodSecurityStatusExceptionsAndUnexpectedFailuresDoNotLeakDetails() throws Exception {
		mvc.perform(get("/denied"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(get("/status"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("The requested resource was not found."));
		mvc.perform(get("/unexpected"))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.message").value("The request could not be completed. Please try again."));
	}

	@RestController
	static class ErrorEndpoints {
		record Input(@NotBlank String title) {
		}

		@GetMapping("/empty")
		ResponseEntity<Void> empty() {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}

		@GetMapping("/custom")
		ResponseEntity<ApiError> custom() {
			return ResponseEntity.status(503).body(new ApiError("UNAVAILABLE", "Please try again."));
		}

		@GetMapping("/fallback")
		ResponseEntity<Map<String, String>> fallback() {
			return ResponseEntity.status(404).body(Map.of("exception", "internal details"));
		}

		@GetMapping("/success")
		Map<String, String> success() {
			return Map.of("value", "unchanged");
		}

		@PostMapping("/validated")
		Input validated(@Valid @RequestBody Input input) {
			return input;
		}

		@GetMapping("/denied")
		void denied() {
			throw new AccessDeniedException("internal permission details");
		}

		@GetMapping("/status")
		void missing() {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "internal lookup details");
		}

		@GetMapping("/unexpected")
		void unexpected() {
			throw new IllegalStateException("database connection details");
		}
	}
}
