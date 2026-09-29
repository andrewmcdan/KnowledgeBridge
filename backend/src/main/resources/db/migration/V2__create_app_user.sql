CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Development accounts (synthetic data only). Passwords: admin123 / user123.
INSERT INTO app_user (email, password_hash, display_name, role) VALUES
    ('admin@acme.example', '$2b$10$ZS1QXBkTCjDtOAZ1Ws6LkOiBDhdOw6N0xskBILNQkx5D3sT4z0efi', 'Acme Admin', 'ADMIN'),
    ('user@acme.example',  '$2b$10$MYM1AvE0lvGiFcXLC3NcZ.hS9E3vnMHZqhaVO83.OfKmAvMs21zFG', 'Acme User',  'USER');
