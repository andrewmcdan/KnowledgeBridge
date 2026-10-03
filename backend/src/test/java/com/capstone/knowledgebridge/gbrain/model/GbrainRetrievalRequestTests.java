package com.capstone.knowledgebridge.gbrain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;

class GbrainRetrievalRequestTests {

	@Test
	void searchRequestsTrimTheQueryAndNormalizeTypes() {
		GbrainSearchRequest request = new GbrainSearchRequest("  travel\npolicy  ", 10, Set.of("Policy", "note"));

		assertThat(request.query()).isEqualTo("travel\npolicy");
		assertThat(request.documentTypes()).containsExactlyInAnyOrder("policy", "note");
		assertThat(new GbrainSearchRequest("q", 1, null).documentTypes()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1, GbrainSearchRequest.MAX_LIMIT + 1})
	void searchRequestsRejectOutOfRangeLimits(int limit) {
		assertValidation(() -> new GbrainSearchRequest("q", limit, Set.of()));
	}

	@Test
	void searchRequestsRejectInvalidQueriesAndTypes() {
		assertValidation(() -> new GbrainSearchRequest(null, 10, Set.of()));
		assertValidation(() -> new GbrainSearchRequest("   ", 10, Set.of()));
		assertValidation(() -> new GbrainSearchRequest("a\u0000b", 10, Set.of()));
		assertValidation(
				() -> new GbrainSearchRequest("x".repeat(GbrainSearchRequest.MAX_QUERY_LENGTH + 1), 10, Set.of()));
		assertValidation(() -> new GbrainSearchRequest("q", 10, Set.of("../person")));
	}

	@Test
	void synthesisRequestsTrimAndBoundTheQuestion() {
		assertThat(new GbrainSynthesisRequest(" Who audits procurement? ").question())
				.isEqualTo("Who audits procurement?");
		assertValidation(() -> new GbrainSynthesisRequest(""));
		assertValidation(
				() -> new GbrainSynthesisRequest("x".repeat(GbrainSynthesisRequest.MAX_QUESTION_LENGTH + 1)));
	}

	@Test
	void retrievalIsHealthyOnlyWhenSemanticReadyAndUndegraded() {
		GbrainRetrieval.Degradation embedFailed = new GbrainRetrieval.Degradation("embed_unavailable",
				"provider_error");

		assertThat(new GbrainRetrieval(true, false, true, List.of()).healthy()).isTrue();
		assertThat(new GbrainRetrieval(false, false, true, List.of()).healthy()).isFalse();
		assertThat(new GbrainRetrieval(true, false, false, List.of()).healthy()).isFalse();
		assertThat(new GbrainRetrieval(true, false, true, List.of(embedFailed)).healthy()).isFalse();
	}

	private static void assertValidation(ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(GbrainException.class,
				exception -> assertThat(exception.code()).isEqualTo(GbrainErrorCode.VALIDATION));
	}

}
