package com.capstone.knowledgebridge.gbrain;

import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.capstone.knowledgebridge.gbrain.model.GbrainHealth;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unauthenticated liveness probe for gbrain's {@code GET /health}. It reports failures as a status instead of throwing
 * so readiness and admin diagnostics can describe a degraded engine.
 */
public class GbrainHealthClient {

	private static final String ENDPOINT = "gbrain health endpoint";

	private final GbrainProperties properties;

	private final JsonMapper jsonMapper;

	private final RestClient restClient;

	GbrainHealthClient(GbrainProperties properties, JsonMapper jsonMapper) {
		this.properties = properties;
		this.jsonMapper = jsonMapper;
		this.restClient = GbrainHttpSupport.restClient(properties.connectTimeout(), properties.readTimeout());
	}

	public GbrainHealth check() {
		if (!properties.enabled()) {
			return GbrainHealth.disabled();
		}
		try {
			JsonNode body = restClient.get()
					.uri(properties.healthUrl())
					.accept(MediaType.APPLICATION_JSON)
					.exchange((request, response) -> {
						GbrainHttpSupport.requireSuccess(response, ENDPOINT);
						return jsonMapper.readTree(
								GbrainHttpSupport.readBounded(response.getBody(),
										properties.maxResponseSize().toBytes()));
					});
			return "ok".equals(body.path("status").asString(""))
					? GbrainHealth.up(body.path("version").asString(null))
					: GbrainHealth.down(GbrainErrorCode.PROTOCOL);
		} catch (ResourceAccessException exception) {
			return GbrainHealth.down(GbrainHttpSupport.transportFailure(exception, ENDPOINT).code());
		} catch (GbrainException exception) {
			return GbrainHealth.down(exception.code());
		} catch (JacksonException exception) {
			return GbrainHealth.down(GbrainErrorCode.PROTOCOL);
		}
	}

}
