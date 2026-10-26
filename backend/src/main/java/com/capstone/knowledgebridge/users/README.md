# users

User accounts and roles backing the `app_user` table (created in `V2__create_app_user.sql`).

`UserAdministrationService` implements admin account summaries and transactional USER-to-ADMIN promotion through the protected `/api/admin/users` routes. It never returns password hashes or enables disabled accounts; role updates require a new login to take effect.

Expected future contents:

- `AppUser` entity — id, email, `password_hash`, display name, `role` (`ADMIN` | `USER`), `enabled`, timestamps.
- `AppUserRepository` — lookup by email for login; listing and role/enabled updates for the admin user-management routes.
- `Role` enum if the string column becomes awkward to work with.

Registration in `auth` hashes exact passwords with the existing encoder and exclusively assigns enabled USER accounts. V12 enforces normalized email uniqueness for enabled and disabled accounts. Existing normalization collisions fail migration without changing account IDs or ownership.

Password hashes are compared only in `auth` via `PasswordEncoder.matches`; they are never returned from any route. `AuthController` currently reads `app_user` with `JdbcTemplate` directly and should switch to the repository once it exists.
