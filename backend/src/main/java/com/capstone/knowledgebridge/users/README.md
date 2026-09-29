# users

User accounts and roles backing the `app_user` table (created in `V2__create_app_user.sql`).

Expected contents:

- `AppUser` entity — id, email, `password_hash`, display name, `role` (`ADMIN` | `USER`), `enabled`, timestamps.
- `AppUserRepository` — lookup by email for login; listing and role/enabled updates for the admin user-management routes.
- `Role` enum if the string column becomes awkward to work with.

Password hashes are compared only in `auth` via `PasswordEncoder.matches`; they are never returned from any route. `AuthController` currently reads `app_user` with `JdbcTemplate` directly and should switch to the repository once it exists.
