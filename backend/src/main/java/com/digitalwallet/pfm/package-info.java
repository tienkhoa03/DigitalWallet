/**
 * PFM module — FR4.x, FR5.x.
 * CQRS for budgets (NFR6): Redis hot read-model + Postgres materialized view; MUST NOT UPDATE
 * ledger tables (see .claude/rules/backend_coding.md §1).
 * Event-time correctness (NFR7): consumers use transaction_timestamp from the Kafka payload,
 * not Instant.now().
 * Layering — subpackages api/, service/, consumer/, persistence/.
 */
package com.digitalwallet.pfm;
