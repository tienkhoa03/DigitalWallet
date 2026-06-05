# 0001 — JWT signing algorithm

## Status

Accepted

## Date

2026-06-05 (accepted with the Phase 1 signup/login merge; originally proposed 2026-05-13)

## Deciders

DigitalWallet backend

## Context

DigitalWallet uses stateless JWT authentication ([../../project-info.md §8](../../project-info.md#8-security-baseline)). The signing algorithm determines token size on the wire, signing cost on the wallet path, and the operational shape of the key material (RSA keypair vs. ECDSA P-256 keypair). The team needs an ADR capturing the alternatives considered and the rationale for the chosen algorithm. Source: [../../project-info.md §10 row 1](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

## Options considered

- **ES256 (ECDSA P-256)** — smaller tokens and keys than RS256, faster signing on the wallet path.
- **RS256 (RSA-SHA-256)** — wider tooling support but larger tokens and slower signing.

## Decision

Use **ES256 (ECDSA P-256)** for all DigitalWallet access tokens. ES256 yields smaller tokens and keys than RS256 and signs faster on the synchronous wallet path, which matters under the `/transfers` P95 budget. Operational specifics, as implemented in Phase 1:

- **Algorithm allow-list:** verification accepts `ES256` only (`smallrye.jwt.verify.algorithm=ES256`). `alg: none` and `HS256` are rejected, closing the classic JWT-confusion attacks (security.md §2).
- **Public verification key:** `backend/src/main/resources/META-INF/jwt-public-key-dev.pem` for dev, resolved via `mp.jwt.verify.publickey.location` (env-overridable per profile). The public key is not secret.
- **Private signing key:** supplied through the `JWT_PRIVATE_KEY` env var only (`app.jwt.private-key`), with no committed default (security.md §1). The test keypair under `src/test/resources/META-INF/` never authenticates a real user.
- **Clock skew:** ≤ 30 seconds on `nbf`/`exp` (`smallrye.jwt.expiration.grace=30`).
- **Token TTL:** 3600 seconds (`app.jwt.ttl-seconds`).
- **`iss` / `aud`:** `digitalwallet` (`JWT_ISSUER`) and `digitalwallet-api` (`JWT_AUDIENCE`); `groups` carries the single role name for `@RolesAllowed`.

## Consequences

- **Easier:** smaller bearer tokens on every request; cheaper signatures on the money path; key material is a compact EC keypair.
- **Harder:** ES256 requires correct EC key handling (PKCS#8 private key, X.509 public key); operators must provision an EC keypair rather than reuse an RSA one.
- **Live with:** a single hard-coded algorithm — rotating to a different curve/algorithm is an ADR-level change, not a config tweak.
- **Revisit if:** a downstream consumer cannot validate ES256, an HSM/KMS mandates RSA, or a CVE affects the P-256 implementation in the verification stack.

## References

- [project-info.md §8 — Security baseline](../../project-info.md#8-security-baseline)
- [.claude/rules/security.md §2 — Authentication](../../.claude/rules/security.md#2-authentication)
- [docs/plans/implementation-plan-phase-1-signup-login.md](../plans/implementation-plan-phase-1-signup-login.md)
