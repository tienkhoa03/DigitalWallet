-- Phase 1 — FR1.1 user identity.
-- Schema mirrors docs/database/README.md `user`.
-- The table name is quoted because `user` collides with the SQL reserved word in Postgres.
-- The unique index on LOWER(email) backs case-insensitive uniqueness (Open Q #10).

CREATE TABLE "user" (
    id              uuid         NOT NULL,
    email           varchar      NOT NULL,
    password_hash   varchar      NOT NULL,
    role            varchar      NOT NULL DEFAULT 'USER',
    base_currency   char(3)      NOT NULL,
    fraud_status    varchar      NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL,
    CONSTRAINT user_pkey PRIMARY KEY (id),
    CONSTRAINT user_role_check CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT user_fraud_status_check CHECK (fraud_status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE UNIQUE INDEX user_email_lower_uniq ON "user" (LOWER(email));
