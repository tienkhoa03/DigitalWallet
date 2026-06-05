-- Phase 1 — FR1.1 account identity.
-- Schema mirrors docs/database/README.md `account`.
-- `account` is a plain identifier in Postgres (no quoting needed, unlike the SQL
-- reserved word `user` this table replaces).
-- The unique index on LOWER(email) backs case-insensitive uniqueness (Open Q #10).

CREATE TABLE account (
    id              uuid         NOT NULL,
    email           varchar      NOT NULL,
    password_hash   varchar      NOT NULL,
    role            varchar      NOT NULL DEFAULT 'USER',
    base_currency   char(3)      NOT NULL,
    fraud_status    varchar      NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL,
    CONSTRAINT account_pkey PRIMARY KEY (id),
    CONSTRAINT account_role_check CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT account_fraud_status_check CHECK (fraud_status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE UNIQUE INDEX account_email_lower_uniq ON account (LOWER(email));
