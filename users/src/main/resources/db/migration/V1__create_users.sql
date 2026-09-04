CREATE TABLE users (
    id       BIGSERIAL PRIMARY KEY,
    role     VARCHAR(32)  NOT NULL,
    email    VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64)  NOT NULL,
    CONSTRAINT uk_users_email    UNIQUE (email),
    CONSTRAINT uk_users_nickname UNIQUE (nickname),
    CONSTRAINT ck_users_role     CHECK (role IN ('PLAYER', 'ADMINISTRATOR'))
);
