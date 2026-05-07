# 📄 PROJECT TOPIC: REAL-TIME DIGITAL WALLET & FRAUD PREVENTION ENGINE

## 1. Summary & Project Objectives

Build a miniature financial system featuring two parallel processing streams:

- **Synchronous Stream (Core Banking):** Handles deposit, withdrawal, and transfer transactions ensuring absolute data accuracy and consistency (ACID properties).
- **Asynchronous Stream (Fraud Engine):** Analyzes the transaction data stream in real-time to detect anomalies (e.g., rapid consecutive transfers, suspected money laundering) and pushes alerts directly to the administrator's dashboard.

## 2. System Architecture & Technology Stack

The project adopts a **Feature-based organization combined with a Layered Architecture**. Code is grouped by feature modules (e.g., Wallet, Fraud, Transaction), and within each feature, it is structured into clear technical layers (Presentation/API, Business/Service, Data Access/Persistence) to maintain separation of concerns.

- **Backend Layer:** Java 21, Quarkus (RESTEasy Classic for JAX-RS, CDI/ArC, Hibernate ORM with Panache, Narayana JTA transactions, Quarkus WebSockets Next).
- **Database & Migration:** PostgreSQL (master data and balance storage), Flyway via `quarkus-flyway` (database schema version management).
- **Message Broker:** Apache Kafka via SmallRye Reactive Messaging (Decoupling the core transaction stream from the risk analysis stream).
- **Caching & In-memory Store:** Redis (Distributed Locks, Rate Limiting, Session management) via `quarkus-redis-client` / Redisson.
- **Time-series Data (Optional/Advanced):** InfluxDB (Storing metrics and historical risk alerts over time for charting).
- **Frontend Layer:** Angular 17+, Tailwind CSS, RxJS, browser WebSocket API.
- **Deployment:** Docker & Docker Compose.

## 3. Functional Requirements (MVP)

### Epic 1: Core Wallet Management

- **FR1.1:** Users can create accounts and internal wallets.
- **FR1.2 - Deposit/Withdraw:** Provide APIs to simulate depositing and withdrawing funds (balance updates).
- **FR1.3 - P2P Transfer:** Users can transfer funds internally to one another using a User ID or Account Number.
- **FR1.4 - Transaction History:** View transaction statements with filters for time periods and transaction types.

### Epic 2: Fraud Detection Engine

_Note: This stream runs in the background and must not cause any latency to the main transaction flow._

- **FR2.1 - Rule 1 (Velocity Check):** Detect if a single account executes more than 5 transactions within 1 minute.
- **FR2.2 - Rule 2 (Volume Check):** Detect if an account performs continuous small transactions that cumulatively exceed a risk threshold (e.g., > $50,000 within 1 hour).
- **FR2.3 - Event Publishing:** Every successful transaction must publish a message (Event) to the `transaction-events` topic in Kafka.

### Epic 3: Real-time Admin Dashboard

- **FR3.1 - Live Metrics:** Display the total number of transactions and the total volume of money circulated throughout the day.
- **FR3.2 - Alert Stream:** Receive alert data via WebSockets. Whenever the Rule Engine catches fraudulent activity, a red notification (toast message) must appear immediately on the Admin screen without requiring a page reload.

## 4. Non-Functional & Core Technical Requirements (Mandatory)

_This section dictates the project's quality and demonstrates your technical engineering mindset._

- **NFR1 - Concurrency Control:**
  - _Essence:_ Must strictly prevent Race Conditions.
  - _Requirement:_ Implement Pessimistic Locking (`SELECT ... FOR UPDATE` via JPA LockModeType) when updating balances. Alternatively, for a more advanced approach, use Distributed Locks via Redis (Redisson) during fund deduction.
- **NFR2 - Data Consistency:**
  - _Essence:_ Money cannot arbitrarily be created or destroyed.
  - _Requirement:_ Apply `@Transactional` (Narayana JTA via Quarkus) correctly. Implement the Outbox Pattern (if time permits) or ensure that committing to the database and publishing the event to Kafka do not result in synchronization failures.
- **NFR3 - Idempotency:**
  - _Requirement:_ Transfer APIs must require an `Idempotency-Key` in the HTTP Header. If a user spam-clicks the "Transfer" button 3 times due to network lag, the server must only deduct the funds once.
- **NFR4 - Code Quality & Testing:**
  - _Requirement:_ Must include Unit Tests using JUnit 5 and Mockito for business classes handling balance operations. Achieve a minimum of 80% Code Coverage at the Service layer.
- **NFR5 - Decoupling:**
  - _Requirement:_ The transfer API handles only two tasks: updating the database and pushing an event to Kafka, before returning a `200 OK`. Analyzing whether a transaction is fraudulent is strictly the responsibility of the Consumer (Kafka Listener) running on a separate thread.

## 5. Data Flow Design for Transfer Transactions

1.  **Frontend (Angular)** sends an HTTP POST request with the payload: `from_wallet`, `to_wallet`, `amount`, `idempotency_key`.
2.  **API Layer (RESTEasy Classic / JAX-RS on Quarkus)** receives the request and validates the payload data.
3.  **Service Layer (Core Wallet):**
    - Checks Redis to verify if the `idempotency_key` has already been processed. If yes -> Return 200 (Skip processing).
    - Opens a Database Transaction.
    - Locks the 2 wallet records (sender and receiver).
    - Deducts funds from the sender, adds funds to the receiver.
    - Writes 2 log records into the `transaction_history` table.
    - Commits the DB Transaction.
4.  **Event Publisher:** Pushes a JSON-formatted message to the Kafka topic `transaction-events`. The server then returns a success response to the Frontend.
5.  **Fraud Engine (Kafka Consumer):**
    - Listens to messages from the topic.
    - Executes calculation logic: Retrieves the past 1-minute history from Redis/DB to count transaction frequency/volume.
    - If a rule violation is detected -> Pushes a message to the `fraud-alerts` topic.
6.  **WebSocket Controller:** Listens to the `fraud-alerts` topic and broadcasts the payload to all connected Admin Angular clients via Quarkus WebSockets Next.
