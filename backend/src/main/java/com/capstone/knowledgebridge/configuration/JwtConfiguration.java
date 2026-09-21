package com.capstone.knowledgebridge.configuration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Symmetric (HS256) JWT signing. The same secret both issues tokens in
 * AuthController and validates them on every request in SecurityConfiguration.
 */
@Configuration
public class JwtConfiguration {

	private final SecretKey secretKey;

	private final Duration tokenTtl;

	public JwtConfiguration(@Value("${knowledgebridge.jwt.secret}") String secret,
			@Value("${knowledgebridge.jwt.ttl:PT8H}") Duration tokenTtl) {
		if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("knowledgebridge.jwt.secret must be at least 32 bytes for HS256");
		}
		this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.tokenTtl = tokenTtl;
	}

	@Bean
	JwtEncoder jwtEncoder() {
		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
	}

	@Bean
	JwtDecoder jwtDecoder() {
		return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	Duration jwtTokenTtl() {
		return tokenTtl;
	}

}
