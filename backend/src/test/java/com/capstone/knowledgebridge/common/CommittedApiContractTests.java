package com.capstone.knowledgebridge.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.capstone.knowledgebridge.auth.AuthController;
import com.capstone.knowledgebridge.knowledge.IngestionStage;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemController;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemStatus;
import com.capstone.knowledgebridge.knowledge.KnowledgeSourceKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Fails CI when backend records or lifecycle enums drift from the committed contract. */
class CommittedApiContractTests {

	private final JsonNode schemas = new JsonMapper().readTree(readContract()).path("components").path("schemas");

	@Test
	void requiredJsonFieldsMatchTheApplicationRecords() {
		assertFields("KnowledgeItem", KnowledgeItemController.KnowledgeItemResponse.class);
		assertFields("ManualKnowledgeItemRequest", KnowledgeItemController.ManualKnowledgeItemRequest.class);
		assertFields("LoginRequest", AuthController.LoginRequest.class);
		assertFields("RegistrationRequest", AuthController.RegistrationRequest.class);
		assertFields("LoginResponse", AuthController.LoginResponse.class);
		assertFields("AuthUser", AuthController.MeResponse.class);
		assertFields("ApiError", ApiError.class);
	}

	@Test
	void publishedLifecycleValuesMatchBackendEnums() {
		JsonNode item = schemas.path("KnowledgeItem").path("properties");
		assertThat(values(item.path("status").path("enum")))
				.containsExactlyElementsOf(names(KnowledgeItemStatus.values()));
		assertThat(values(item.path("ingestionStage").path("enum")))
				.containsExactlyElementsOf(names(IngestionStage.values()));
		assertThat(values(item.path("sourceKind").path("enum")))
				.containsExactlyElementsOf(names(KnowledgeSourceKind.values()));
	}

	private void assertFields(String schema, Class<?> record) {
		assertThat(values(schemas.path(schema).path("required")))
				.containsExactlyElementsOf(
						Arrays.stream(record.getRecordComponents()).map(component -> component.getName()).toList());
	}

	private static List<String> values(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(node -> values.add(node.asString()));
		return values;
	}

	private static List<String> names(Enum<?>[] values) {
		return Arrays.stream(values).map(Enum::name).toList();
	}

	private static String readContract() {
		try {
			return Files.readString(Path.of("../Docs/openapi.json"));
		} catch (java.io.IOException exception) {
			throw new IllegalStateException("Cannot read committed API contract", exception);
		}
	}
}
