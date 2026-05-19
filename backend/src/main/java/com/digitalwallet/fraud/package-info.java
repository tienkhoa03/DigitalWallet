/**
 * Fraud module — FR2.1, FR2.2, FR2.3, FR2.4, FR2.5.
 * Hybrid enforcement model per docs/decisions/0010-fraud-enforcement-model.md: synchronous pre-check
 * (NFR9) lives in shared/ and runs before the wallet lock; this module owns async cross-event
 * analysis, suspension policy, and the `fraud-alerts` topic producer.
 * Layering per .claude/rules/backend_coding.md §1 — subpackages consumer/, service/, event/.
 */
package com.digitalwallet.fraud;
