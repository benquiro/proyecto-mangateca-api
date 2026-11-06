CREATE TABLE users
(
  id                    UUID         NOT NULL,
  email                 VARCHAR(254) NOT NULL,
  display_name          VARCHAR(100) NOT NULL,
  password_hash         VARCHAR(255) NOT NULL,
  role                  VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
  enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
  failed_login_attempts INTEGER      NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
  locked_until          TIMESTAMPTZ,
  created_at            TIMESTAMPTZ  NOT NULL,
  updated_at            TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX users_email_lower_uk ON users (lower(email));

CREATE TABLE refresh_tokens
(
  id          UUID        NOT NULL,
  user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  token_hash  BYTEA       NOT NULL CHECK (octet_length(token_hash) = 32),
  family_id   UUID        NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ,
  replaced_by UUID REFERENCES refresh_tokens (id),
  created_at  TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT refresh_tokens_token_hash_uk UNIQUE (token_hash)
);

CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
