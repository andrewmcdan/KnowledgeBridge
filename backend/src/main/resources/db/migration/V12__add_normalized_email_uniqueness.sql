-- Preserve existing account identities and ownership; conflicting legacy accounts need manual resolution.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM app_user GROUP BY lower(btrim(email)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Cannot enable registration: existing accounts share a normalized email; resolve duplicate accounts before retrying V12';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_app_user_normalized_email ON app_user (lower(btrim(email)));
