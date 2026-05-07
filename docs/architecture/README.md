# Architecture

## §1 What it is

Digital Wallet is a miniature financial system with two parallel processing streams: a **synchronous core-banking stream** that handles deposits, withdrawals, and P2P transfers under ACID guarantees, and an **asynchronous fraud-detection stream** that consumes a transaction event log in real time and pushes alerts to an admin dashboard over WebSockets. The two streams are decoupled by Apache Kafka so that fraud analysis cannot add latency or failure modes to the request path.

## §2 Tech stack at a glance

```
  ┌──────────────────────┐                     ┌──────────────────────┐
  │  Angular 17+ client  │   HTTPS / JSON     │  Quarkus backend     │
  │  (Tailwind, RxJS)    │ ─────────────────▶ │  (RESTEasy + CDI)    │
  └──────────┬───────────┘                     └──────────┬───────────┘
             │                                            │ JPA
             │  WebSocket (fraud alerts, FR3.2)           ▼
             │                                    ┌──────────────┐
             │                                    │ PostgreSQL   │
             │                                    │ (+ Flyway)   │
             │                                    └──────────────┘
             │                                            │
             │                                            │ publish
             │                                            ▼
             │                                    ┌──────────────────────┐
             │                                    │ Kafka                │
             │                                    │  topic:              │
             │                                    │  transaction-events  │
             │                                    └──────────┬───────────┘
             │                                               │ consume
             │                                               ▼
             │                                    ┌──────────────────────┐
             │                                    │ Fraud Engine         │
             │                                    │ (Kafka consumer)     │
             │                                    └──────────┬───────────┘
             │                                               │ Redis: counters, locks
             │                                               ▼
             │                                    ┌──────────────────────┐
             │     subscribe                      │ Kafka                │
             │ ◀───────────────────────────────── │  topic: fraud-alerts │
                                                  └──────────────────────┘
```

The two Kafka boxes are the same broker carrying different topics.

## §3 Backend layering (spec — not yet implemented)

Per [../../README.md §2](../../README.md), feature-based + layered. Target structure:

```
backend/
└── src/main/java/.../
    ├── wallet/
    │   ├── api/         # JAX-RS (RESTEasy Classic) resources
    │   ├── service/     # CDI-managed business logic, @Transactional
    │   └── persistence/ # JPA entities + Panache repositories
    ├── transaction/
    │   ├── api/
    │   ├── service/
    │   ├── persistence/
    │   └── event/       # SmallRye Reactive Messaging emitter to transaction-events
    ├── fraud/
    │   ├── consumer/    # SmallRye @Incoming listener for transaction-events
    │   ├── rule/        # velocity & volume rules
    │   └── publisher/   # SmallRye emitter to fraud-alerts
    ├── admin/
    │   ├── api/         # live metrics REST
    │   └── ws/          # Quarkus WebSockets Next endpoint, subscribes to fraud-alerts
    └── shared/
        ├── idempotency/ # Redis-backed Idempotency-Key store
        └── config/      # CDI producers, env binding
```

## §4 Frontend layering (spec — not yet implemented)

Angular 17+ standalone components, RxJS for reactive state, Tailwind for styling, native browser WebSocket for the alert stream.

```
frontend/src/app/
├── core/         # http interceptors, websocket service, auth (verify)
├── features/
│   ├── wallet/   # deposit, withdraw, transfer, history
│   └── admin/    # metrics dashboard, fraud-alert toasts
└── shared/       # ui primitives, pipes
```

## §5 How modules connect

| Concern | Convention |
|---|---|
| API base URL | (verify — not specified in spec) |
| Auth scheme | **(unspecified by spec)** — see [decisions/](../decisions/) once chosen |
| Wire format | JSON over HTTPS |
| Real-time channel | Quarkus WebSockets Next (server) ↔ browser WebSocket (client) |
| Inter-service messaging | Kafka topics `transaction-events`, `fraud-alerts` |
| Idempotency | `Idempotency-Key` HTTP header on transfer endpoints, stored in Redis |

## §6 Auth flow

**The spec does not prescribe an auth mechanism.** Once chosen, document the numbered flow here and reference the implementing classes. The choice itself belongs in [decisions/](../decisions/).

## §7 Config & profiles (spec — not yet implemented)

Quarkus profiles: `dev`, `test`, `prod` (built-in) plus a `local` profile for the Docker-Compose stack. Configuration lives in `src/main/resources/application.properties` and is overridden per profile via `%dev.*` / `%test.*` / `%prod.*` keys, or via the corresponding env variables. Per-environment values to externalize:

| Property (Quarkus) | Env variable | Purpose |
|---|---|---|
| `quarkus.datasource.jdbc.url` | `QUARKUS_DATASOURCE_JDBC_URL` | Postgres connection |
| `quarkus.datasource.username` | `QUARKUS_DATASOURCE_USERNAME` | Postgres user |
| `quarkus.datasource.password` | `QUARKUS_DATASOURCE_PASSWORD` | Postgres password |
| `kafka.bootstrap.servers` | `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker(s) |
| `quarkus.redis.hosts` | `QUARKUS_REDIS_HOSTS` | Redis connection (idempotency, locks, fraud counters) |
| `app.influxdb.url`, `app.influxdb.token` | `APP_INFLUXDB_URL`, `APP_INFLUXDB_TOKEN` | Optional time-series sink |
| `app.idempotency.key-ttl-seconds` | `APP_IDEMPOTENCY_KEY_TTL_SECONDS` | TTL on stored idempotency keys |
| `app.fraud.velocity.window-seconds` | `APP_FRAUD_VELOCITY_WINDOW_SECONDS` | Velocity rule window (default 60) |
| `app.fraud.volume.window-seconds` | `APP_FRAUD_VOLUME_WINDOW_SECONDS` | Volume rule window (default 3600) |
| `app.fraud.volume.threshold` | `APP_FRAUD_VOLUME_THRESHOLD` | Volume rule cumulative threshold (default 50000) |

## §8 Deployment topology

A single Docker Compose stack containing the application plus Postgres, Kafka, Redis, and optionally InfluxDB. No CI pipeline is committed yet.
