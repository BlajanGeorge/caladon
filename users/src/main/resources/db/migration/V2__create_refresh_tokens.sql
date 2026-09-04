-- Server-side store of issued refresh tokens, so they can be revoked before expiry.
-- id is the JWT "jti" claim of the refresh token.
CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
