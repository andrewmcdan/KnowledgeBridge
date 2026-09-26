package com.capstone.knowledgebridge.configuration;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

	/** Name of the JWT claim that carries the application role (ADMIN or USER). */
	public static final String ROLE_CLAIM = "role";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// Stateless bearer-token API: no session cookie, so no CSRF token to check.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.authorizeHttpRequests(authorize -> authorize
						// Let Boot's /error forward render 400/404/500 bodies instead of turning them
						// into 401.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
						.requestMatchers("/api/health", "/api/auth/login").permitAll()
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				// Validates the Authorization: Bearer <jwt> header on every other request.
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

		return http.build();
	}

	/** Maps the token's "role" claim to a ROLE_* authority so hasRole() works. */
	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			String role = jwt.getClaimAsString(ROLE_CLAIM);
			return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
		});
		return converter;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
