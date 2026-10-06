package com.capstone.knowledgebridge.gbrain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Obtains short-lived bearer tokens through the OAuth client-credentials grant and caches each one until shortly before
 * it expires. Tokens and the client secret never appear in exception messages or logs.
 */
class GbrainTokenProvider {

	static final String SCOPE = "read write";

	static final Duration EXPIRY_SKEW = Duration.ofSeconds(30);

	/** Used when the token response omits the RFC 6749 {@code expires_in} recommendation. */
	static final Duration DEFAULT_LIFETIME = Duration.ofMinutes(5);

	private static final String ENDPOINT = "gbrain token endpoint";

	private final GbrainProperties properties;

	private final RestClient restClient;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	private String accessToken;

	private Instant refreshAt;

	GbrainTokenProvider(GbrainProperties properties, JsonMapper jsonMapper, Clock clock) {
		this.properties = properties;
		this.restClient = GbrainHttpSupport.restClient(properties.connectTimeout(), properties.readTimeout());
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	synchronized String accessToken() {
		if (accessToken == null || !clock.instant().isBefore(refreshAt)) {
			requestToken();
		}
		return accessToken;
	}

	/** Drops the cached token if it is the one gbrain rejected, so the next call obtains a fresh token. */
	synchronized void invalidate(String rejectedToken) {
		if (rejectedToken.equals(accessToken)) {
			accessToken = null;
		}
	}

	private void requestToken() {
		if (!properties.hasCredentials()) {
			throw new GbrainException(GbrainErrorCode.CONFIGURATION,
					"gbrain OAuth client credentials are not configured; run scripts/provision-gbrain.ps1");
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", properties.oauthClientId());
		form.add("client_secret", properties.oauthClientSecret());
		form.add("scope", SCOPE);
		Instant requestedAt = clock.instant();
		JsonNode body;
		try {
			body = restClient.post()
					.uri(properties.oauthTokenUrl())
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.accept(MediaType.APPLICATION_JSON)
					.body(form)
					.exchange((request, response) -> {
						// OAuth reports invalid_client/invalid_grant/invalid_scope as 400 or 401.
						if (response.getStatusCode().value() == 400) {
							throw new GbrainException(GbrainErrorCode.UNAUTHORIZED, ENDPOINT + " rejected the client");
						}
						GbrainHttpSupport.requireSuccess(response, ENDPOINT);
						return jsonMapper.readTree(
								GbrainHttpSupport.readBounded(response.getBody(),
										properties.maxResponseSize().toBytes()));
					});
		} catch (ResourceAccessException exception) {
			throw GbrainHttpSupport.transportFailure(exception, ENDPOINT);
		} catch (JacksonException exception) {
			throw new GbrainException(GbrainErrorCode.PROTOCOL, ENDPOINT + " returned malformed JSON", exception);
		}

		JsonNode token = body.path("access_token");
		if (!token.isString() || token.asString().isBlank()
				|| !"bearer".equalsIgnoreCase(body.path("token_type").asString(""))) {
			throw new GbrainException(GbrainErrorCode.PROTOCOL, ENDPOINT + " did not return a bearer access token");
		}
		JsonNode expiresIn = body.path("expires_in");
		Duration lifetime = expiresIn.isIntegralNumber() && expiresIn.asLong() > 0
				? Duration.ofSeconds(expiresIn.asLong())
				: DEFAULT_LIFETIME;
		accessToken = token.asString();
		refreshAt = requestedAt.plus(lifetime).minus(EXPIRY_SKEW);
	}

}
