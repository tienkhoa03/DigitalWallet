/**
 * Cross-cutting helpers shared across feature modules.
 * Owns: money type, idempotency middleware, outbox poller, security primitives, lock helper,
 * rate-limit middleware, audit-log writer, validation annotations, websocket multiplex.
 * MUST NOT depend on any feature module; feature modules import from here, not the reverse
 * (.claude/rules/backend_coding.md §1).
 */
package com.digitalwallet.shared;
