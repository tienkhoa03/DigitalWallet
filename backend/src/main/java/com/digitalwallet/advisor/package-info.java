/**
 * AI advisor module — FR6.x.
 * LLM isolation (NFR8): outbound calls wrapped in a SmallRye circuit breaker. Endpoints follow
 * async request-reply — HTTP 202 + WebSocket result.
 * Provider TBD per docs/decisions/0002-llm-provider.md (open question #2 in project-info.md §16).
 * Layering — subpackages api/, service/, client/.
 */
package com.digitalwallet.advisor;
