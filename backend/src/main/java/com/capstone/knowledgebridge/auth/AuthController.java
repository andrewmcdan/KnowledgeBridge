package com.capstone.knowledgebridge.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.knowledgebridge.configuration.SecurityConfiguration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	public record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	public record LoginResponse(String token, Instant expiresAt, MeResponse user) {
	}

	public record MeResponse(long id, String email, String displayName, String role) {
	}

	/** Row from app_user; password hash is only ever compared, never returned. */
	private record AppUser(long id, String email, String passwordHash, String displayName, String role,
			boolean enabled) {
	}

	private final JdbcTemplate jdbcTemplate;

	private final PasswordEncoder passwordEncoder;

	private final JwtEncoder jwtEncoder;

	private final Duration tokenTtl;

	public AuthController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
			Duration jwtTokenTtl) {
		this.jdbcTemplate = jdbcTemplate;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.tokenTtl = jwtTokenTtl;
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		List<AppUser> matches = jdbcTemplate.query(
				"SELECT id, email, password_hash, display_name, role, enabled FROM app_user WHERE email = ?",
				(rs, rowNum) -> new AppUser(rs.getLong("id"), rs.getString("email"), rs.getString("password_hash"),
						rs.getString("display_name"), rs.getString("role"), rs.getBoolean("enabled")),
				request.email().trim().toLowerCase());

		// One generic failure path: never reveal whether the email or the password was
		// wrong.
		if (matches.isEmpty() || !matches.get(0).enabled()
				|| !passwordEncoder.matches(request.password(), matches.get(0).passwordHash())) {
			log.info("Rejected login for {}", request.email());
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		AppUser user = matches.get(0);
		Instant now = Instant.now();
		Instant expiresAt = now.plus(tokenTtl);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("knowledgebridge")
				.subject(Long.toString(user.id()))
				.issuedAt(now)
				.expiresAt(expiresAt)
				.claim("email", user.email())
				.claim("name", user.displayName())
				.claim(SecurityConfiguration.ROLE_CLAIM, user.role())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

		log.info("Issued token for user {} ({})", user.id(), user.role());
		return ResponseEntity.ok(new LoginResponse(token, expiresAt, toMeResponse(user)));
	}

	/**
	 * JWTs are stateless, so there is nothing to invalidate server-side; the client discards the token. The route
	 * exists so the frontend has a single place to call if a server-side deny-list is added later.
	 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
		log.info("Logout for user {}", jwt.getSubject());
		return ResponseEntity.noContent().build();
	}

	/**
	 * The token was already verified by the security filter; just read its claims.
	 */
	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		return new MeResponse(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("email"),
				jwt.getClaimAsString("name"), jwt.getClaimAsString(SecurityConfiguration.ROLE_CLAIM));
	}

	private static MeResponse toMeResponse(AppUser user) {
		return new MeResponse(user.id(), user.email(), user.displayName(), user.role());
	}

}
