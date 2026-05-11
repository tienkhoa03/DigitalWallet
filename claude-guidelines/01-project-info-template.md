# Project Info — Template

> **How to use this file**
>
> 1. Copy this file to the **root of your project repo** and rename it to `project-info.md`.
> 2. Fill in every section. Where a section truly doesn't apply, write `N/A — <one-line reason>` rather than deleting it.
> 3. Replace every `<PLACEHOLDER>` and every `// EXAMPLE` block with your project's reality.
> 4. Keep the section numbering. The bootstrap prompts ([prompts/](prompts/)) reference sections by number.
> 5. After CLAUDE.md and `docs/` are generated, this file becomes scaffolding — either delete it or archive it under `docs/_archive/`. It is not source of truth.
>
> See [02-how-to-fill-template.md](02-how-to-fill-template.md) for a field-by-field guide and common pitfalls.

---

## §1 Project Identity

- **Project name:** `<NAME>`
- **One-line description:** `<one sentence — what the system does, who uses it>`
- **Primary value:** `<the single most important thing this product proves or delivers>`
- **Status:** `<greenfield | retrofitting an existing repo | partial implementation>`
- **Repository:** `<git URL or "local-only">`
- **License:** `<MIT | Apache-2.0 | proprietary | N/A>`

---

## §2 Stakeholders & users

### 2.1 User personas

| Persona | Goals | Frequency |
|---|---|---|
| `<e.g. End user>` | `<what they want to accomplish>` | `<daily / weekly / occasional>` |
| `<e.g. Administrator>` | `<…>` | `<…>` |

### 2.2 Roles in the system (if multi-tenant or RBAC)

| Role | Permissions | Notes |
|---|---|---|
| `<role>` | `<read X, write Y, admin Z>` | `<…>` |

> If single-tenant or no RBAC yet, write `N/A — single role for MVP`.

---

## §3 Architecture style

- **High-level shape:** `<monolith | modular monolith | microservices | event-driven | hexagonal | layered | feature-based + layered | …>`
- **Synchronous vs. asynchronous boundaries:**
  - `<e.g. transfer path is synchronous; fraud analysis is asynchronous over Kafka>`
- **Major streams / paths:** `<list each independent processing path and what decouples them>`
- **Deployment topology:** `<single container | docker-compose stack | k8s cluster | serverless | …>`
- **Real-time channel (if any):** `<WebSocket | SSE | polling | N/A>`

### 3.1 Module / package organization

State the **target** layout — the bootstrap prompts will create `docs/` and rules that reference it.

```
<repo>/
├── <module-a>/
│   ├── api/         # presentation
│   ├── service/     # business logic
│   ├── persistence/ # data access
│   └── event/       # messaging in/out (if applicable)
├── <module-b>/
│   └── …
└── shared/          # cross-module utilities
```

State the **organising principle** in one sentence:

> e.g. "Feature-based + layered. Group code by feature module, separate the standard layers (`api/`, `service/`, `persistence/`) inside each."

---

## §4 Tech stack (mandated)

> Be specific. "Database: SQL" is too vague — the rules generated from this section will be vague too.

### 4.1 Backend

| Concern | Choice | Version target | Reason / constraint |
|---|---|---|---|
| Language | `<Java / TypeScript / Go / Python / …>` | `<e.g. Java 21>` | `<LTS, team familiarity, …>` |
| Framework | `<Quarkus / Spring Boot / NestJS / Django / …>` | `<x.y LTS>` | `<…>` |
| API style | `<REST / GraphQL / gRPC>` | — | `<…>` |
| API library | `<JAX-RS (RESTEasy Classic) / Express / FastAPI / …>` | — | `<…>` |
| ORM / persistence | `<Hibernate ORM + Panache / Prisma / SQLAlchemy / …>` | — | `<…>` |
| Migrations | `<Flyway / Liquibase / Prisma migrate / Alembic / …>` | — | `<…>` |
| Validation | `<Bean Validation / Zod / Pydantic / …>` | — | `<…>` |
| Build tool | `<Maven / Gradle / pnpm / pip / …>` | — | `<…>` |

### 4.2 Frontend (if applicable)

| Concern | Choice | Version target | Reason / constraint |
|---|---|---|---|
| Framework | `<Angular / React / Vue / Svelte / N/A>` | `<x.y>` | `<…>` |
| Language | `<TypeScript / JavaScript>` | `<x.y strict>` | `<…>` |
| Styling | `<Tailwind / styled-components / CSS modules / …>` | — | `<…>` |
| State mgmt | `<signals / Redux / Zustand / RxJS / context / …>` | — | `<…>` |
| Forms | `<Reactive Forms / React Hook Form / Formik / …>` | — | `<…>` |
| Package mgr | `<npm / pnpm / yarn>` | — | `<…>` |

### 4.3 Persistence & data

| Store | Purpose | Constraint |
|---|---|---|
| `<Postgres>` | `<authoritative ledger>` | `<ACID, BigDecimal money>` |
| `<Redis>` | `<cache, locks, rate-limit>` | `<not a persistence layer>` |
| `<MongoDB / S3 / …>` | `<…>` | `<…>` |

### 4.4 Messaging & integration

| Tech | Topics / queues | Purpose |
|---|---|---|
| `<Kafka / RabbitMQ / N/A>` | `<transaction-events, fraud-alerts>` | `<decouple sync path from async fraud engine>` |

### 4.5 Testing & quality

| Layer | Tool | Floor |
|---|---|---|
| Unit (backend) | `<JUnit 5 + Mockito>` | `<≥80% service-layer line coverage>` |
| Integration | `<Testcontainers>` | `<every public repository method>` |
| Unit (frontend) | `<Karma + Jasmine / Jest>` | `<every reactive branch>` |
| E2E (optional) | `<Playwright / Cypress / N/A>` | `<smoke per epic>` |
| Coverage tool | `<JaCoCo / Istanbul>` | — |

### 4.6 Deployment

- **Container runtime:** `<Docker | Podman | N/A>`
- **Orchestration:** `<Docker Compose | Kubernetes | bare-metal | …>`
- **Cloud target (if any):** `<AWS / GCP / Azure / on-prem / N/A>`
- **CI/CD:** `<GitHub Actions / GitLab CI / Jenkins / N/A>`

---

## §5 Functional requirements (Epics & FRs)

> Number every requirement. Downstream rules and tests cite them.

### Epic 1: `<name>`

- **FR1.1:** `<…>`
- **FR1.2:** `<…>`

### Epic 2: `<name>`

- **FR2.1:** `<…>`
- **FR2.2:** `<…>`

> Add as many Epics as needed. Out-of-scope items go in §11.

---

## §6 Non-functional requirements / invariants

> These become the **non-negotiable** sections of `CLAUDE.md` and the test cases that block merges.

| ID | Invariant | Why it matters | Enforcement layer |
|---|---|---|---|
| NFR1 | `<e.g. balance updates use pessimistic locking>` | `<prevent race conditions>` | `<service layer>` |
| NFR2 | `<e.g. money cannot be created or destroyed>` | `<correctness, audit>` | `<DB CHECK + service guard>` |
| NFR3 | `<e.g. transfer endpoints require Idempotency-Key>` | `<retry safety>` | `<idempotency middleware>` |
| NFR4 | `<e.g. ≥80% service-layer coverage>` | `<regression safety>` | `<JaCoCo gate in CI>` |
| NFR5 | `<e.g. fraud analysis off the request thread>` | `<latency isolation>` | `<Kafka consumer, never sync path>` |

---

## §7 External integrations

| System | Direction | Protocol | Auth | Failure handling |
|---|---|---|---|---|
| `<LLM API>` | outbound | `<HTTPS REST>` | `<API key>` | `<circuit breaker, fallback to rule-based>` |
| `<SMTP>` | outbound | `<SMTP>` | `<credentials>` | `<retry with backoff>` |

> If none, write `N/A — no external integrations in MVP`.

---

## §8 Security baseline

- **Auth scheme:** `<JWT (ES256/RS256) | OIDC | session cookie | unspecified — to be decided in ADR>`
- **Authorization model:** `<roles | ABAC | tenant + role | …>`
- **PII handled:** `<list — email, full name, account number, payment data, …>`
- **Compliance constraints:** `<GDPR / SOC 2 / HIPAA / PCI-DSS / none>`
- **Rate limiting:** `<which endpoints, what limits>`
- **Secret management:** `<env vars via 12-factor / Vault / cloud secret manager / …>`
- **HTTPS-only:** `<yes — production / N/A — internal>`

---

## §9 Domain glossary

> The vocabulary the team agrees on. The bootstrap prompts use it to keep `docs/domain-knowledge/` consistent.

| Term | Meaning in this product |
|---|---|
| `<Account>` | `<a user identity — holds one or more wallets>` |
| `<Wallet>` | `<a balance-bearing record owned by an account>` |
| `<Transfer>` | `<a two-leg atomic operation: debit sender, credit receiver>` |
| `<Idempotency Key>` | `<client-supplied UUID guaranteeing at-most-once side effects>` |

---

## §10 Open architectural decisions (ADRs to write)

> Decisions that **are not yet made** but will need ADRs. The bootstrap prompts seed empty ADRs for each row so you can fill them later.

| # | Decision needed | Options on the table | Blocking? |
|---|---|---|---|
| 1 | `<e.g. JWT vs session cookie>` | `<JWT ES256 / session cookie>` | `<blocks auth flow>` |
| 2 | `<e.g. Outbox vs publish-after-commit>` | `<full Outbox / commit-then-publish on request thread>` | `<blocks transfer service>` |

---

## §11 Explicit non-goals (out of scope)

> Lists things a stakeholder might reasonably expect to be in scope but are deliberately excluded. Surfacing this here prevents fictional rules being generated about them.

- `<e.g. multi-currency / FX>`
- `<e.g. real bank integration / settlement>`
- `<e.g. mobile native apps>`
- `<e.g. SSO with external IdPs in MVP>`

---

## §12 Development workflow

- **Branch model:** `<trunk-based / GitFlow / GitHub Flow>`
- **Default branch:** `<main / master>`
- **PR / MR style:** `<GitHub / GitLab / Bitbucket / Gerrit>`
- **Commit convention:** `<Conventional Commits / freeform>`
- **Code review:** `<required reviewers, blocking checks>`
- **Pre-commit hooks:** `<linter / formatter / gitleaks / none>`
- **Coverage gate in CI:** `<yes — threshold / no>`

---

## §13 Coding conventions (highest-level, project-wide)

> Section 13 is on purpose **short**. Detail belongs in `.claude/rules/`. State the **principles** here — the prompts in step 3 expand them into rule files.

- **Naming:** `<e.g. classes PascalCase, files kebab-case, constants UPPER_SNAKE>`
- **DI / IoC style:** `<constructor injection only / @Inject fields / …>`
- **Error model:** `<typed exception hierarchy with error_key + message / Result<T,E> / …>`
- **Logging library:** `<SLF4J / Pino / structlog / …>` — what to log per level
- **DTOs:** `<separate from entities, never expose entities from the API>`
- **Documentation:** `<OpenAPI / generated / handwritten>`
- **Money / currency:** `<BigDecimal + numeric(19,4) / minor units / …>` (if financial)
- **Timestamps:** `<UTC, timestamptz, ISO-8601 / Unix epoch / …>`

---

## §14 Environment & configuration

> One row per env variable the system will read. The bootstrap prompts use this to scaffold `docs/architecture/` and per-environment config files.

| Property | Env variable | Purpose | Default | Profiles |
|---|---|---|---|---|
| `<datasource.url>` | `<DB_URL>` | `<DB connection>` | — | `dev/test/prod` |
| `<kafka.brokers>` | `<KAFKA_BOOTSTRAP_SERVERS>` | `<broker>` | — | `dev/test/prod` |

---

## §15 Reference materials

- **Product spec (PRD):** `<path or link>`
- **Design / wireframes:** `<Figma link / N/A>`
- **Prior art / similar systems:** `<links — for ADR context>`
- **External standards:** `<RFC numbers, ISO specs, …>`

---

## §16 Open questions to answer before bootstrapping

> Anything here that is `❓ Unanswered` blocks the bootstrap. Either answer it, mark it `⏳ Deferred` with a one-line reason, or remove it.

| # | Question | Status | Notes |
|---|---|---|---|
| 1 | `<e.g. JWT or session cookie?>` | `❓ Unanswered` | `<blocks rule §9 in backend_coding.md>` |
| 2 | `<…>` | `✅ Answered` | `<…>` |
| 3 | `<…>` | `⏳ Deferred` | `<post-MVP, no decision needed now>` |

---

## §17 Optional sections

> Delete this whole section if none apply.

### 17.1 Performance budget

| Endpoint / scenario | P95 latency | Throughput |
|---|---|---|
| `<POST /transfers>` | `<200 ms>` | `<100 rps>` |

### 17.2 Observability requirements

- **Metrics sink:** `<Prometheus / Datadog / N/A>`
- **Traces:** `<OpenTelemetry / N/A>`
- **Logs:** `<JSON to stdout / ELK / Loki / …>`

### 17.3 Accessibility floor (if frontend exists)

- **WCAG level:** `<AA / AAA / N/A>`
- **Keyboard-only paths:** `<every interactive element / …>`
- **Screen-reader support:** `<yes / nice-to-have / N/A>`

---

*End of template. After every section is filled in, proceed to [03-initialization-workflow.md](03-initialization-workflow.md).*
