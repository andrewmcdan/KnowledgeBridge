package com.capstone.knowledgebridge.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.capstone.knowledgebridge.common.GlobalExceptionHandler;
import com.capstone.knowledgebridge.gbrain.GbrainEvaluationService;
import com.capstone.knowledgebridge.usage.UsageService;

import jakarta.validation.Validation;
import tools.jackson.databind.json.JsonMapper;

class QuestionValidationTests {

	@Test
	void sharedFrontendExamplesMatchTheApiRequestPolicy() throws Exception {
		var cases = JsonMapper.builder().build().readTree(java.nio.file.Files.readString(
				java.nio.file.Path.of("../config/query-normalization-cases.json")));
		try (var factory = Validation.buildDefaultValidatorFactory()) {
			for (var example : cases) {
				var search = new QueryController.SearchRequest(example.path("question").asString(), 10);
				var synthesis = new QueryController.SynthesisRequest(example.path("question").asString());
				assertThat(search.question()).isEqualTo(example.path("normalized").asString());
				assertThat(synthesis.question()).isEqualTo(search.question());
				assertThat(factory.getValidator().validate(search).isEmpty())
						.isEqualTo(example.path("valid").asBoolean());
				assertThat(factory.getValidator().validate(synthesis).isEmpty())
						.isEqualTo(example.path("valid").asBoolean());
			}
		}
	}

	@Test
	void invalidQuestionsUseSafeApplicationErrorsBeforeCallingTheService() throws Exception {
		QueryService service = mock(QueryService.class);
		var mvc = MockMvcBuilders.standaloneSetup(new QueryController(service, mock(UsageService.class),
				mock(GbrainEvaluationService.class))).setControllerAdvice(new GlobalExceptionHandler()).build();
		var json = JsonMapper.builder().build();
		for (String endpoint : List.of("search", "synthesize")) {
			int max = endpoint.equals("search") ? 1000 : 2000;
			for (String question : List.of(" \t\n\u2003", "secret\u0000text", "x".repeat(max + 1))) {
				mvc.perform(post("/api/query/" + endpoint).contentType("application/json")
						.content(json.writeValueAsString(Map.of("question", question))))
						.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
						.andExpect(jsonPath("$.message").isNotEmpty())
						.andExpect(content()
								.string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
			}
			mvc.perform(post("/api/query/" + endpoint).contentType("application/json").content("{}"))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Question is required."));
		}
		verifyNoInteractions(service);
	}

	@Test
	void normalizationAndBoundariesMatchTheDownstreamModels() {
		try (var factory = Validation.buildDefaultValidatorFactory()) {
			var validator = factory.getValidator();
			for (int length : List.of(1000, 1001, 2000, 2001)) {
				String question = " \t\u2003" + "x".repeat(length) + "\n\u001c";
				var search = new QueryController.SearchRequest(question, 50);
				var synthesis = new QueryController.SynthesisRequest(question);
				var evaluation = new QueryController.EvaluationRequest(question, "model", List.of());
				assertThat(search.question()).hasSize(length);
				assertThat(synthesis.question()).isEqualTo(search.question());
				assertThat(evaluation.question()).isEqualTo(search.question());
				assertThat(validator.validate(search).isEmpty()).isEqualTo(length <= 1000);
				assertThat(validator.validate(synthesis).isEmpty()).isEqualTo(length <= 2000);
				assertThat(validator.validate(evaluation).isEmpty()).isEqualTo(length <= 2000);
			}
			assertThat(validator.validate(new QueryController.EvaluationRequest(null, "model", List.of())))
					.isNotEmpty();
			assertThat(validator.validate(new QueryController.SearchRequest("Q", 0))).isNotEmpty();
			assertThat(validator.validate(new QueryController.SearchRequest("Q", 51))).isNotEmpty();
		}
	}
}
