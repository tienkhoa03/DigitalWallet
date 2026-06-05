#!/usr/bin/env python3
"""Seed the DigitalWallet Trello board from the MVP master roadmap.

Creates Kanban lists, names the board labels by area, and creates one card per
implementation phase (Phase 0-9 backend, F1-F5 frontend) with a description and
a "Tasks" checklist. Source: docs/plans/implementation-plan-mvp-master.md.

Idempotent: re-running will NOT duplicate lists, labels, cards, or checklists.
Cards are matched by exact name; an existing card is left untouched.

Credentials are read from the environment (TRELLO_API_KEY or TRELLO_KEY, plus
TRELLO_TOKEN). If unset, the script falls back to parsing ./.env at runtime.
The secret values are never printed.

Usage:
    python3 scripts/seed_trello_board.py [board_shortlink]   # default: BBVddj3q
"""

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.trello.com/1"
DEFAULT_BOARD = "BBVddj3q"
WRITE_PAUSE = 0.08  # gentle throttle to stay under Trello's per-token rate limit


def load_env_file(path=".env"):
    """Populate TRELLO_* from a dotenv file if not already in the environment."""
    if os.environ.get("TRELLO_TOKEN") and (
        os.environ.get("TRELLO_API_KEY") or os.environ.get("TRELLO_KEY")
    ):
        return
    if not os.path.exists(path):
        return
    with open(path, encoding="utf-8") as fh:
        for raw in fh:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            line = line[len("export "):].strip() if line.startswith("export ") else line
            if "=" not in line:
                continue
            name, _, value = line.partition("=")
            name = name.strip()
            value = value.strip().strip('"').strip("'")
            if name.startswith("TRELLO_") and name not in os.environ:
                os.environ[name] = value


load_env_file()
KEY = os.environ.get("TRELLO_API_KEY") or os.environ.get("TRELLO_KEY")
TOKEN = os.environ.get("TRELLO_TOKEN")
BOARD = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_BOARD

if not KEY or not TOKEN:
    sys.exit("ERROR: set TRELLO_API_KEY (or TRELLO_KEY) and TRELLO_TOKEN "
             "(env or ./.env). Nothing was changed.")


def call(method, path, data=None):
    qs = urllib.parse.urlencode({"key": KEY, "token": TOKEN})
    url = f"{API}{path}?{qs}"
    body = urllib.parse.urlencode(data).encode() if data else None
    req = urllib.request.Request(url, data=body, method=method)
    if body:
        req.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            txt = resp.read().decode()
            return json.loads(txt) if txt else None
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")[:300]
        sys.exit(f"ERROR: HTTP {exc.code} on {method} {path}: {detail}")
    finally:
        if method in ("POST", "PUT", "DELETE"):
            time.sleep(WRITE_PAUSE)


# --- Area -> label colour. Renames the board's 6 default (unnamed) labels. -----
LABEL_BY_COLOR = {
    "green": "Backend",
    "blue": "Frontend",
    "orange": "Infra/DevOps",
    "purple": "Docs/ADR",
    "red": "Security",
    "yellow": "Testing",
}

LISTS = ["Backlog", "In Progress", "In Review", "Done"]


def ensure_labels(board_id):
    existing = call("GET", f"/boards/{board_id}/labels")
    by_color = {}
    for lab in existing:
        by_color.setdefault(lab["color"], lab)
    name_to_id = {}
    for color, name in LABEL_BY_COLOR.items():
        lab = by_color.get(color)
        if lab:
            if lab.get("name") != name:
                call("PUT", f"/labels/{lab['id']}", {"name": name})
                print(f"  label set:    {color:7s} -> {name}")
            else:
                print(f"  label ok:     {color:7s} -> {name}")
            name_to_id[name] = lab["id"]
        else:
            lab = call("POST", "/labels",
                       {"name": name, "color": color, "idBoard": board_id})
            name_to_id[name] = lab["id"]
            print(f"  label new:    {color:7s} -> {name}")
    return name_to_id


def ensure_lists(board_id):
    existing = {lst["name"]: lst for lst in call("GET", f"/boards/{board_id}/lists")}
    name_to_id = {}
    for index, name in enumerate(LISTS, start=1):
        if name in existing:
            name_to_id[name] = existing[name]["id"]
            print(f"  list ok:      {name}")
        else:
            lst = call("POST", "/lists",
                       {"name": name, "idBoard": board_id, "pos": index * 65536})
            name_to_id[name] = lst["id"]
            print(f"  list new:     {name}")
    return name_to_id


def ensure_card(card, list_id, existing_cards, label_ids):
    name = card["name"]
    if name in existing_cards:
        print(f"  card ok:      {name}")
        return False
    ids = ",".join(label_ids[area] for area in card["labels"])
    created = call("POST", "/cards", {
        "idList": list_id,
        "name": name,
        "desc": card["desc"],
        "idLabels": ids,
        "pos": "bottom",
    })
    checklist = call("POST", "/checklists", {"idCard": created["id"], "name": "Tasks"})
    for item in card["tasks"]:
        call("POST", f"/checklists/{checklist['id']}/checkItems",
             {"name": item, "checked": "false"})
    print(f"  card new:     {name}  (+{len(card['tasks'])} tasks)")
    return True


# --- The 15 phase tickets, transcribed from the MVP master roadmap §9. ---------
CARDS = [
    {
        "name": "Phase 0 — Layout reconcile",
        "labels": ["Infra/DevOps"],
        "desc": (
            "**Goal:** Reconcile the repo to the documented baseline. No business code yet.\n"
            "**NFR:** establishes the NFR4 JaCoCo gate.\n"
            "**Agents/Skills:** @backend-developer, Skill(backend-verify)\n"
            "**Plan:** docs/plans/implementation-plan-phase-0-layout-reconcile.md"
        ),
        "tasks": [
            "Plan: /make-plan phase-0-layout-reconcile",
            "Rename digital-wallet-api/ -> backend/",
            "Re-root package org.acme -> com.digitalwallet",
            "Delete starter GreetingResource + its tests",
            "pom.xml: JaCoCo with >=80% gate on com/digitalwallet/*/service/**",
            "pom.xml: Testcontainers Postgres + JUnit Jupiter",
            "pom.xml: Hibernate Validator + SmallRye JWT (REST only)",
            "pom.xml: Quarkus Micrometer + Prometheus",
            "Set quarkus.hibernate-orm.database.generation=none",
            "application.properties: DB block from architecture README §7",
            "CI: drop frontend job (returns in F1); point docker-images at backend/Dockerfile",
            "Build: /implement-plan + Skill(backend-verify) green",
        ],
    },
    {
        "name": "Phase 1 — Signup + Login (JWT ES256)",
        "labels": ["Backend", "Security"],
        "desc": (
            "**Goal:** User identity (FR1.1). Ship shared/exception, shared/security "
            "(ES256 JWT), shared/validation. ADR 0001 -> Accepted.\n"
            "**FR/NFR:** FR1.1; security §1/§2/§5.\n"
            "**Agents/Skills:** @backend-developer, Skill(backend-create-rest-api), "
            "Skill(backend-create-unit-test)"
        ),
        "tasks": [
            "Plan: /make-plan phase-1-signup-login",
            "shared/exception: DomainException + JAX-RS mapper + canonical error envelope",
            "shared/security: ES256 JWT issuance + verification (alg allow-list, <=30s skew)",
            "shared/validation: @CurrencyCode validator",
            "Flyway V1: user table (id, email, password_hash, base_currency, role, fraud_status, created_at)",
            "Argon2id password hashing",
            "POST /users (signup)",
            "POST /auth/login (enumeration-resistant auth.invalid_credentials)",
            "Security headers (HSTS, CSP, X-Frame-Options, nosniff, Referrer-Policy, Permissions-Policy) + CORS allow-list",
            "Unit + integration tests (Testcontainers Postgres), JaCoCo >=80%",
            "ADR 0001 (JWT signing algorithm) -> Accepted",
            "Build + Skill(code-review) + Skill(backend-verify) green",
        ],
    },
    {
        "name": "Phase 2 — Open Wallet",
        "labels": ["Backend"],
        "desc": (
            "**Goal:** Wallet creation (FR1.1 wallet). Flyway V2 wallet table — "
            "UNIQUE(user_id,label), NO unique on currency_code (ADR 0006).\n"
            "**FR/NFR:** FR1.1; RBAC at controller AND service, owner-scope.\n"
            "**Agents/Skills:** @backend-developer, Skill(backend-create-rest-api)"
        ),
        "tasks": [
            "Plan: /make-plan phase-2-open-wallet",
            "Flyway V2: wallet table, UNIQUE(user_id,label), no unique on currency_code",
            "POST /wallets (open wallet) — @Valid + @CurrencyCode",
            "GET /wallets (list owned wallets)",
            "RBAC: @RolesAllowed(USER) at controller AND service re-check",
            "Ownership scoping on reads",
            "wallet.duplicate_label (409) path",
            "Unit + integration tests, JaCoCo >=80%",
            "Auth tests: unauthenticated / wrong-role / wrong-tenant",
            "Build + Skill(code-review) + Skill(backend-verify) green",
        ],
    },
    {
        "name": "Phase 3 — Deposit + shared money infra",
        "labels": ["Backend"],
        "desc": (
            "**Goal:** Simplest money leg (FR1.2) + the shared money/idempotency/outbox/lock "
            "stack. Honours NFR1 (Redis lock -> DB PESSIMISTIC_WRITE -> outbox WRITE -> commit "
            "-> release) and NFR3 (replay). Adds docker-compose (Postgres + Redis only).\n"
            "**Gates:** Phases 4–7 reuse this infra.\n"
            "**Agents/Skills:** @backend-developer, Skill(backend-create-rest-api), "
            "Skill(backend-create-unit-test)"
        ),
        "tasks": [
            "Plan: /make-plan phase-3-deposit",
            "shared/money: BigDecimal helpers, numeric(19,4) JPA converter, FX snapshot type",
            "shared/idempotency: idempotency_record V3, IN_FLIGHT/COMPLETED, salted-hash key logging",
            "shared/outbox: outbox_event V3 + writer (WRITE-ONLY, no poller, no Kafka)",
            "shared/lock: Redis distributed-lock helper (Testcontainers Redis)",
            "Flyway V3: transaction table",
            "POST /wallets/{walletId}/deposits (Idempotency-Key required)",
            "NFR1: Redis lock -> PESSIMISTIC_WRITE -> ledger + outbox in one tx -> commit -> release",
            "NFR3: replay returns original outcome; conflicting body+key -> idempotency.replay_conflict",
            "docker-compose.yml (Postgres + Redis only)",
            "Tests: NFR1 concurrent mutation, NFR2 atomic ledger+outbox, NFR3 replay",
            "Build + Skill(code-review) + Skill(backend-verify) green",
        ],
    },
    {
        "name": "Phase 4 — Withdraw",
        "labels": ["Backend"],
        "desc": (
            "**Goal:** FR1.2 second leg. Reuse Phase 3 infra; add wallet.insufficient_funds (422). "
            "Tighten zero/negative/boundary amount tests.\n"
            "**Agents/Skills:** @backend-developer, Skill(backend-create-rest-api)"
        ),
        "tasks": [
            "Plan: /make-plan phase-4-withdraw",
            "POST /wallets/{walletId}/withdrawals (Idempotency-Key required)",
            "wallet.insufficient_funds (422)",
            "Reuse Redis lock + PESSIMISTIC_WRITE + outbox WRITE",
            "Boundary tests: amount 0, 0.0001, negative, overflow",
            "Replay + auth tests",
            "JaCoCo >=80%",
            "Build + Skill(code-review) + Skill(backend-verify) green",
        ],
    },
    {
        "name": "Phase 5 — Transfer (two-leg atomic)",
        "labels": ["Backend"],
        "desc": (
            "**Goal:** FR1.3 two-leg transfer. Debit+credit in one tx, shared transfer_id, "
            "FX snapshot on both legs (ADR 0006), recipient via to_user_id only. Deterministic "
            "Redis-lock order by wallet_id UUID to avoid deadlock.\n"
            "**Agents/Skills:** @backend-developer, Skill(backend-create-rest-api), "
            "Skill(backend-create-unit-test)"
        ),
        "tasks": [
            "Plan: /make-plan phase-5-transfer",
            "POST /transfers (Idempotency-Key required)",
            "Two ledger rows sharing transfer_id, single tx",
            "FX snapshot on both legs (cross-currency)",
            "Recipient identity via to_user_id only; transfer.recipient_not_found",
            "transfer.same_wallet, wallet.currency_mismatch, transfer.fx_rate_missing",
            "Deterministic Redis-lock acquisition order (wallet_id UUID order)",
            "Concurrent two-wallet test (both directions)",
            "Replay + auth + boundary tests, JaCoCo >=80%",
            "Build + Skill(code-review) + Skill(backend-verify) green",
        ],
    },
    {
        "name": "Phase 6 — Rate limiting on POST /transfers",
        "labels": ["Backend", "Security"],
        "desc": (
            "**Goal:** shared/ratelimit Redis token bucket, 10/min/user on POST /transfers -> "
            "429 + Retry-After + ratelimit.exceeded. Env RATELIMIT_TRANSFER_PER_MINUTE (default 10).\n"
            "**Note:** precedes F4 by construction.\n"
            "**Agents/Skills:** @backend-developer"
        ),
        "tasks": [
            "Plan: /make-plan phase-6-rate-limiting",
            "shared/ratelimit: Redis token bucket keyed (user_id, endpoint)",
            "Apply to POST /transfers (10/min/user)",
            "429 + Retry-After header + ratelimit.exceeded errorKey",
            "Env var RATELIMIT_TRANSFER_PER_MINUTE (default 10)",
            "Rate-limit test: 11th transfer/min -> 429",
            "Build + Skill(code-review) + Skill(backend-verify) green",
        ],
    },
    {
        "name": "Phase 7 — Statement read",
        "labels": ["Backend"],
        "desc": (
            "**Goal:** FR1.4 statement. GET /wallets/{walletId}/statement, paginated "
            "(page/pageSize cap 100), sort-key whitelist (backend_coding §10), filter from/to/type. "
            "NO write locks, owner-scope.\n"
            "**Agents/Skills:** @backend-developer, Skill(backend-create-rest-api)"
        ),
        "tasks": [
            "Plan: /make-plan phase-7-statement",
            "GET /wallets/{walletId}/statement",
            "Pagination page/pageSize, capped at 100 (clamp, do not error)",
            "Sort-key whitelist (no JPQL interpolation)",
            "Filters: from/to date, type in deposit/withdraw/transfer_debit/transfer_credit",
            "No PESSIMISTIC_WRITE on read path",
            "Owner-scope check + auth tests",
            "N+1 guard test (Hibernate Statistics), JaCoCo >=80%",
            "Build + Skill(code-review) + Skill(backend-verify) green",
        ],
    },
    {
        "name": "Phase 8 — Observability + performance budget",
        "labels": ["Backend", "Infra/DevOps"],
        "desc": (
            "**Goal:** ADR 0011 Proposed -> Accepted. Micrometer + Prometheus (/q/metrics), "
            "structured JSON stdout logs (no PII per backend_coding §11), X-Request-Id propagation. "
            "Capture real /transfers P95 vs project-info §17.1 dev-target.\n"
            "**Agents/Skills:** @backend-developer"
        ),
        "tasks": [
            "Plan: /make-plan phase-8-observability",
            "Quarkus Micrometer + Prometheus endpoint (/q/metrics)",
            "Structured JSON stdout logs honouring forbidden-content rules",
            "X-Request-Id propagation through services + into logs",
            "Capture real P95 for POST /transfers on docker-compose",
            "Compare vs project-info §17.1 dev-target (note NFR9-deferred context)",
            "ADR 0011 (observability stack) -> Accepted",
            "Build + Skill(code-review) + Skill(backend-verify) green",
        ],
    },
    {
        "name": "Phase 9 — Backend MVP acceptance",
        "labels": ["Testing"],
        "desc": (
            "**Goal:** End-to-end backend acceptance via curl + RestAssured @QuarkusTest smoke: "
            "signup -> login -> open USD+EUR -> deposit -> withdraw -> transfer -> cross-currency "
            "transfer (FX) -> 11th transfer hits rate limit -> statement filtered. Update CLAUDE.md "
            "(Epic 1 backend complete; NFR9 deferred).\n"
            "**Agents/Skills:** orchestrator, Skill(code-review), Skill(backend-verify)"
        ),
        "tasks": [
            "Plan: /make-plan phase-9-acceptance",
            "RestAssured @QuarkusTest golden-path smoke suite",
            "curl walkthrough: signup->login->2 wallets->deposit->withdraw->transfer->FX transfer->rate-limit->statement",
            "Confirm NFR1 / NFR2(write) / NFR3 coverage present",
            "Security matrix complete: unauth / wrong-role / wrong-tenant / replay / boundary / rate-limit",
            "Update CLAUDE.md Project Status: backend Epic 1 complete, NFR9 deferred",
            "Skill(code-review) + Skill(backend-verify) green on main",
        ],
    },
    {
        "name": "Phase F1 — Frontend bootstrap + shared",
        "labels": ["Frontend"],
        "desc": (
            "**Goal:** Create frontend/ (Vite 5 + React 18 + TS5 strict + Tailwind 3 + RTK/RTK "
            "Query + RHF + Zod + Vitest + RTL + Playwright + pnpm). app/ providers, routes/ guards, "
            "ErrorBoundary, shared error reporter, money/format, idempotency/uuidv7, config, "
            "RTK Query baseQuery (JWT inject + 401->logout, typed ApiError). Restore CI frontend job.\n"
            "**Agents/Skills:** @frontend-developer, Skill(frontend-implement-ui-component), "
            "Skill(frontend-verify)"
        ),
        "tasks": [
            "Plan: /make-plan phase-F1-frontend-bootstrap",
            "Scaffold Vite + React 18 + TS strict + Tailwind 3 + RTK + RHF + Zod + Vitest + Playwright (pnpm)",
            "Dockerfile + docker-compose (join dw-net) + nginx.conf (/api proxy + WS upgrade)",
            "app/ provider stack; routes/ <RequireAuth> + <RequireRole role=USER>; <ErrorBoundary>",
            "shared/money/format.ts (Intl.NumberFormat on decimal strings)",
            "shared/idempotency/uuidv7.ts; shared/config.ts (VITE_API_BASE_URL)",
            "RTK Query baseQuery: JWT inject, 401->logout+redirect, typed ApiError {error_key,message}",
            "Design tokens in tailwind.config.ts",
            "Restore frontend job in .github/workflows/ci.yml",
            "Build + Skill(frontend-verify) green",
        ],
    },
    {
        "name": "Phase F2 — Auth (signup + login)",
        "labels": ["Frontend", "Security"],
        "desc": (
            "**Goal:** features/auth — RTK Query (POST /users, POST /auth/login), auth.slice "
            "(JWT in-memory + opaque remembered flag), LoginPage + SignupPage (RHF + Zod mirroring "
            "backend rules). Public routes /login, /signup.\n"
            "**Agents/Skills:** @frontend-developer, Skill(frontend-implement-ui-component)"
        ),
        "tasks": [
            "Plan: /make-plan phase-F2-auth",
            "features/auth/auth.api.ts (POST /users, POST /auth/login)",
            "features/auth/auth.slice.ts (JWT in-memory; localStorage opaque flag only)",
            "<LoginPage> + <SignupPage> with RHF + zodResolver",
            "Zod schemas: email, password (min len), base_currency ISO 4217 immutable",
            "Public routes /login, /signup",
            "Vitest specs (renderWithProviders, msw at baseQuery)",
            "Build + Skill(frontend-verify) green",
        ],
    },
    {
        "name": "Phase F3 — Wallets + deposit + withdraw",
        "labels": ["Frontend"],
        "desc": (
            "**Goal:** features/wallet — RTK Query (GET/POST /wallets, deposits, withdrawals). "
            "WalletsPage renders label AND currency_code; OpenWalletForm; Deposit/Withdraw pages "
            "(decimal-string amount, UUIDv7 Idempotency-Key reused, MoneyDisplay). Routes behind "
            "<RequireAuth>.\n"
            "**Agents/Skills:** @frontend-developer, Skill(frontend-implement-ui-component)"
        ),
        "tasks": [
            "Plan: /make-plan phase-F3-wallets",
            "features/wallet/wallet.api.ts (GET/POST /wallets, deposits, withdrawals)",
            "<WalletsPage> — render label AND currency_code (never currency alone)",
            "<OpenWalletForm> — unique-label hint, ISO 4217 picker over full list",
            "<DepositPage> + <WithdrawPage> — shared mutation form",
            "Idempotency-Key UUIDv7 at form mount, reused across resubmits",
            "MoneyDisplay for amounts (never Number.toFixed)",
            "Routes /wallets, /wallets/new, /wallets/:id/deposit, /wallets/:id/withdraw behind RequireAuth",
            "Vitest specs + Skill(frontend-verify) green",
        ],
    },
    {
        "name": "Phase F4 — Transfer + statement",
        "labels": ["Frontend"],
        "desc": (
            "**Goal:** features/transfer (POST /transfers) + features/statement (GET statement). "
            "TransferPage (source picker label+currency, recipient to_user_id, FX preview, UUIDv7 key, "
            "Retry-After countdown on 429). StatementPage (virtualized, from/to+type filters, stable "
            "transaction.id keys).\n"
            "**Agents/Skills:** @frontend-developer, Skill(frontend-implement-ui-component)"
        ),
        "tasks": [
            "Plan: /make-plan phase-F4-transfer-statement",
            "features/transfer/transfer.api.ts (POST /transfers)",
            "<TransferPage> — source picker (label+currency), recipient to_user_id, FX preview note",
            "Idempotency-Key UUIDv7; Retry-After countdown on 429 ratelimit.exceeded",
            "features/statement/statement.api.ts (GET /wallets/{id}/statement, paginated + filters)",
            "<StatementPage> — virtualized table (>200 rows), from/to + type filters, stable keys",
            "Routes /transfers/new, /wallets/:id/statement",
            "Vitest specs + Skill(frontend-verify) green",
        ],
    },
    {
        "name": "Phase F5 — Frontend smoke + Playwright",
        "labels": ["Frontend", "Testing"],
        "desc": (
            "**Goal:** Vitest per-page (AAA, msw at baseQuery, data-test/getByRole priority, money "
            "decimal-string asserts, XSS regression). One Playwright golden-path. Mark MVP frontend "
            "complete; update CLAUDE.md.\n"
            "**Agents/Skills:** @frontend-developer, Skill(frontend-verify), Skill(code-review)"
        ),
        "tasks": [
            "Plan: /make-plan phase-F5-smoke",
            "Vitest specs per page: AAA layout, msw at baseQuery (not raw fetch)",
            "Query priority data-test -> getByRole -> getByText",
            "Money-formatter assertions on decimal strings (not Number)",
            "XSS regression: <script> string renders as text",
            "Playwright golden-path: signup->login->open USD+EUR->deposit->withdraw->transfer->FX transfer->statement filtered",
            "Update CLAUDE.md Project Status (frontend MVP complete)",
            "Skill(frontend-verify) + Skill(code-review) green",
        ],
    },
]


def main():
    print(f"Resolving board {BOARD} ...")
    board = call("GET", f"/boards/{BOARD}")
    board_id = board["id"]
    print(f"Board: {board['name']}  ({board.get('shortUrl', board_id)})\n")

    print("Labels:")
    label_ids = ensure_labels(board_id)
    print("\nLists:")
    list_ids = ensure_lists(board_id)
    backlog_id = list_ids["Backlog"]

    print("\nCards (-> Backlog):")
    existing_cards = {c["name"]: c for c in call("GET", f"/boards/{board_id}/cards")}
    created = 0
    for card in CARDS:
        if ensure_card(card, backlog_id, existing_cards, label_ids):
            created += 1

    print(f"\nDone. {created} card(s) created, "
          f"{len(CARDS) - created} already present. "
          f"Total phase cards: {len(CARDS)}.")
    print(f"Open the board: {board.get('shortUrl', '')}")


if __name__ == "__main__":
    main()
