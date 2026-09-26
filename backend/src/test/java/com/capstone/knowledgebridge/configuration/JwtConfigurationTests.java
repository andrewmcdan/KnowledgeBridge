package com.capstone.knowledgebridge.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtConfigurationTests {

    @Test
    void rejectsSecretsShorterThanHs256Requires() {
        assertThatThrownBy(() -> new JwtConfiguration("too-short", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void exposesEncoderDecoderAndConfiguredTtl() {
        Duration ttl = Duration.ofMinutes(45);
        JwtConfiguration configuration = new JwtConfiguration(
                "a-secret-that-is-definitely-at-least-thirty-two-bytes", ttl);

        assertThat(configuration.jwtEncoder()).isInstanceOf(JwtEncoder.class);
        assertThat(configuration.jwtDecoder()).isInstanceOf(JwtDecoder.class);
        assertThat(configuration.jwtTokenTtl()).isEqualTo(ttl);
    }
}
