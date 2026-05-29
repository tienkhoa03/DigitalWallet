/**
 * Cross-cutting domain exception hierarchy and JAX-RS exception mappers.
 *
 * <p>Every domain-level failure surfaces through the sealed
 * {@link com.digitalwallet.shared.exception.DomainException} hierarchy and the single
 * {@link com.digitalwallet.shared.exception.DomainExceptionMapper}. See
 * {@code .claude/rules/backend_coding.md §8}.
 */
package com.digitalwallet.shared.exception;
