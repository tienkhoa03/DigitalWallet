/**
 * {@code user/} module service layer — signup and login.
 *
 * <p>{@code @Transactional} boundaries live here (not on the resource or repository) per
 * {@code .claude/rules/backend_coding.md §3}. JaCoCo gate enforces ≥ 80 % line coverage on
 * this package (NFR4).
 */
package com.digitalwallet.user.service;
