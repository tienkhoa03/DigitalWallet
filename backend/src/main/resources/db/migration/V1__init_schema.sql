-- V1__init_schema.sql
-- Initial schema skeleton. Per .claude/rules/backend_coding.md §13:
--   - Forward-only Flyway, versioned SQL.
--   - Entity + migration land in the same PR; this V1 only contains the cross-cutting
--     ledger / outbox / idempotency / audit / fraud-status tables required for the
--     synchronous money path (CLAUDE.md "Synchronous stream") and NFR2 / NFR3 / NFR9.
-- Feature-owned columns (FX rates, budget buckets, advisor history, etc.) ship in V2+
-- migrations alongside the corresponding entities (docs/database/migrations.md).

-- ============================================================
-- account
-- ============================================================
CREATE TABLE account (
    id              UUID            PRIMARY KEY,
    email           VARCHAR(254)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(255)    NOT NULL,
    fraud_status    VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT account_fraud_status_chk CHECK (fraud_status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_account_email ON account (email);

-- ============================================================
-- role_assignment (RBAC; docs/decisions/0009-rbac-roles.md)
-- ============================================================
CREATE TABLE role_assignment (
    account_id      UUID            NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    role            VARCHAR(32)     NOT NULL,
    granted_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    granted_by      UUID            REFERENCES account (id),
    PRIMARY KEY (account_id, role),
    CONSTRAINT role_assignment_role_chk CHECK (role IN ('USER', 'ADMIN', 'FRAUD_ANALYST'))
);

-- ============================================================
-- wallet (FR1.1; an account MAY own multiple wallets in the same
-- currency, disambiguated by `label`. See docs/decisions/0006-multi-currency-model.md)
-- ============================================================
CREATE TABLE wallet (
    id              UUID            PRIMARY KEY,
    account_id      UUID            NOT NULL REFERENCES account (id) ON DELETE RESTRICT,
    currency_code   VARCHAR(3)      NOT NULL,
    label           VARCHAR(64)     NOT NULL,
    balance         NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT wallet_unique_label UNIQUE (account_id, label),
    CONSTRAINT wallet_balance_nonneg CHECK (balance >= 0),
    CONSTRAINT wallet_label_nonempty CHECK (length(trim(label)) > 0)
);

CREATE INDEX idx_wallet_account          ON wallet (account_id);
CREATE INDEX idx_wallet_account_currency ON wallet (account_id, currency_code);

-- ============================================================
-- transaction (ledger; FR1.2 / FR1.3 / FR1.4)
-- ============================================================
CREATE TABLE transaction (
    id                      UUID            PRIMARY KEY,
    from_wallet_id          UUID            REFERENCES wallet (id),
    to_wallet_id            UUID            REFERENCES wallet (id),
    type                    VARCHAR(32)     NOT NULL,
    amount                  NUMERIC(19, 4)  NOT NULL,
    currency_code           VARCHAR(3)      NOT NULL,
    fx_rate                 NUMERIC(19, 8),
    transaction_timestamp   TIMESTAMPTZ     NOT NULL,
    idempotency_key         UUID,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT transaction_type_chk CHECK (type IN (
        'DEPOSIT', 'WITHDRAW', 'TRANSFER_DEBIT', 'TRANSFER_CREDIT', 'FX_LEG'
    )),
    CONSTRAINT transaction_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_transaction_from_wallet_ts ON transaction (from_wallet_id, transaction_timestamp);
CREATE INDEX idx_transaction_to_wallet_ts   ON transaction (to_wallet_id, transaction_timestamp);
CREATE INDEX idx_transaction_event_time     ON transaction (transaction_timestamp);

-- ============================================================
-- outbox_event (NFR2; backend_coding.md §15)
-- ============================================================
CREATE TABLE outbox_event (
    id              UUID            PRIMARY KEY,
    aggregate_type  VARCHAR(64)     NOT NULL,
    aggregate_id    UUID            NOT NULL,
    event_type      VARCHAR(128)    NOT NULL,
    payload         JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

-- ============================================================
-- idempotency_record (NFR3; backend_coding.md §2)
-- ============================================================
CREATE TABLE idempotency_record (
    idempotency_key     UUID            NOT NULL,
    account_id          UUID            NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    request_hash        VARCHAR(128)    NOT NULL,
    response_status     INTEGER         NOT NULL,
    response_body       JSONB           NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (idempotency_key, account_id)
);

CREATE INDEX idx_idempotency_created ON idempotency_record (created_at);

-- ============================================================
-- audit_log (append-only; security.md §2, §3)
-- ============================================================
CREATE TABLE audit_log (
    id                  BIGSERIAL       PRIMARY KEY,
    actor_account_id    UUID,
    subject_account_id  UUID,
    action              VARCHAR(128)    NOT NULL,
    details             JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_actor   ON audit_log (actor_account_id, created_at);
CREATE INDEX idx_audit_log_subject ON audit_log (subject_account_id, created_at);
CREATE INDEX idx_audit_log_action  ON audit_log (action, created_at);

-- ============================================================
-- fraud_event (FR2.1 / FR2.2 / FR2.4 breach records)
-- ============================================================
CREATE TABLE fraud_event (
    id              UUID            PRIMARY KEY,
    account_id      UUID            NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    transaction_id  UUID,
    kind            VARCHAR(64)     NOT NULL,
    details         JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_event_account_ts ON fraud_event (account_id, created_at);
