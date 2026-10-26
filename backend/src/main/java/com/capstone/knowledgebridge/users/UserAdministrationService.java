package com.capstone.knowledgebridge.users;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Account management deliberately exposes no password hashes or arbitrary role updates. */
@Service
public class UserAdministrationService {

	public record ManagedUser(long id, String email, String displayName, String role, boolean enabled) {
	}

	private static final Logger log = LoggerFactory.getLogger(UserAdministrationService.class);
	private static final String USER_FIELDS = "id, email, display_name, role, enabled";
	private static final RowMapper<ManagedUser> USER_ROW = (row, index) -> new ManagedUser(row.getLong("id"),
			row.getString("email"), row.getString("display_name"), row.getString("role"), row.getBoolean("enabled"));
	private final JdbcTemplate jdbc;

	public UserAdministrationService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<ManagedUser> list() {
		return jdbc.query("SELECT " + USER_FIELDS + " FROM app_user ORDER BY lower(display_name), id", USER_ROW);
	}

	@Transactional
	public ManagedUser promote(long userId, long actorId) {
		List<ManagedUser> matches = jdbc.query("SELECT " + USER_FIELDS + " FROM app_user WHERE id = ? FOR UPDATE",
				USER_ROW, userId);
		if (matches.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
		}
		ManagedUser user = matches.get(0);
		if (!user.enabled()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Disabled accounts cannot be promoted.");
		}
		if (user.role().equals("USER")) {
			jdbc.update("UPDATE app_user SET role = 'ADMIN', updated_at = now() WHERE id = ?", userId);
			log.info("Admin role granted: actorUserId={} targetUserId={} previousRole=USER newRole=ADMIN", actorId,
					userId);
		}
		return new ManagedUser(user.id(), user.email(), user.displayName(), "ADMIN", user.enabled());
	}
}
