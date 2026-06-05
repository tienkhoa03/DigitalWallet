package com.digitalwallet.shared.security;

/**
 * RBAC role enum per ADR 0009.
 *
 * <p>Only {@code USER} and {@code ADMIN} ship in MVP — the {@code FRAUD_ANALYST} role is
 * deferred (see {@code docs/decisions/0009-rbac-roles.md}). The {@code .name()} of each
 * entry MUST match the string carried in the JWT {@code groups} claim and the
 * {@code @RolesAllowed(...)} annotation.
 */
public enum AccountRole {
    USER,
    ADMIN
}
