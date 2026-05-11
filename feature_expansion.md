# 📄 EXPANDED PROJECT TOPIC: REAL-TIME DIGITAL WALLET, FRAUD PREVENTION & AI-DRIVEN PERSONAL FINANCE MANAGER (PFM)

## 1. Summary & Project Objectives
Build a comprehensive financial ecosystem integrating three core pillars:
* **Core Banking:** Synchronous processing of deposits, withdrawals, and transfers with strict ACID compliance.
* **Fraud Engine:** Asynchronous, real-time detection of suspicious activities using stream processing.
* **AI-Driven Personal Finance Manager (PFM):** A proactive, intelligence-led module that helps users plan budgets, tracks spending behavior against goals, and provides AI-generated, contextual recommendations to optimize financial health.

## 2. System Architecture & Technology Stack (Expanded)
The architecture evolves from a simple Hexagonal/Layered monolith into an **Event-Driven Microservices-ready structure**.
* **Backend Framework:** Java 21, Jakarta EE 10 (or Spring Boot 3 equivalent if preferred for MVP).
* **AI Integration:** Integration with external LLM APIs (e.g., Gemini, OpenAI) via function calling for personalized financial advice.
* **Data Persistence:**
    * PostgreSQL: Relational data (Wallets, Transactions, Budgets, Users).
    * MongoDB or Elasticsearch (Optional addition): Storing unstructured AI recommendation logs and user interaction history for fast retrieval.
* **Message Broker:** Apache Kafka (Handling Core Transactions, Fraud Alerts, and now Spending Events for the PFM).
* **Caching & Compute:** Redis (Distributed Locks, Rate Limiting) + Redis TimeSeries/Sorted Sets for real-time budget tracking.
* **Scheduling:** Quartz Scheduler or Jakarta EE Timer Service (for end-of-month summaries and periodic budget reviews).

## 3. New Epic: AI-Driven Personal Finance Manager (PFM)

### 3.1. Smart Budgeting & Goal Tracking
* **FR4.1 - Multi-Bucket Budgeting:** Users can create monthly budget plans categorized into "buckets" (e.g., Groceries, Entertainment, Bills). Each transaction (transfer/withdrawal) must include an optional `category_id` payload.
* **FR4.2 - Real-Time Budget Allocation:** As transactions flow through the system, the PFM module consumes them asynchronously to update the utilized amount in the respective budget bucket.
* **FR4.3 - Rolling Budget Thresholds:** Users can set soft limits (e.g., "Alert me when I reach 80% of my Entertainment budget").

### 3.2. Proactive Alerting & Dynamic Adjustments (Event-Driven)
* **FR5.1 - Threshold Breach Alerts:** When a transaction pushes a budget bucket past its defined threshold, an asynchronous event is triggered to notify the user via WebSocket or Push Notification.
* **FR5.2 - Predictive Overspending Warning:** Utilize historical transaction velocity (similar to Fraud Velocity Check but applied to categories) to warn users mid-month: *"At your current spending rate, you will exceed your grocery budget by the 20th."*

### 3.3. AI Financial Advisor Integration
* **FR6.1 - Contextual Spending Analysis:** At the end of the month (or upon user request), the system aggregates the user's spending data (grouped by category, compared against the plan) and sends an anonymized prompt to an LLM.
* **FR6.2 - AI-Generated Actionable Insights:** The LLM returns personalized, structured advice. Example: *"You saved 15% on dining out this month but overspent on subscriptions. Consider canceling unused services to allocate more to your emergency fund."*
* **FR6.3 - Dynamic Budget Auto-Correction:** The AI suggests an optimized budget plan for the *next* month based on the actual spending reality of the *current* month, which the user can accept with one click.

## 4. Technical Challenges & Architectural Highlights of the PFM Epic

Adding PFM features introduces complex state management and aggregation problems in a distributed environment.

### NFR6 - Real-Time Aggregation vs. Database Bottlenecks
* **Challenge:** Updating the budget usage `(utilized_amount)` in PostgreSQL for every single transaction will cause severe contention (row locks) and slow down the database, especially during peak times.
* **Technical Solution (CQRS & Materialized Views/Redis):** 
    * Implement the **CQRS (Command Query Responsibility Segregation)** pattern. The write model (Core Wallet) only appends to the `TRANSACTION` table.
    * The read model (PFM Dashboard) relies on an asynchronous Kafka consumer that updates a fast in-memory store (like a Redis Hash or Redis JSON) representing the user's current budget state.
    * Alternatively, use PostgreSQL **Materialized Views** refreshed periodically, combined with in-memory deltas for sub-second dashboard rendering.

### NFR7 - Handling Out-of-Order Events (Time Travel)
* **Challenge:** If the Kafka cluster experiences a network partition, transaction events might arrive at the PFM consumer out of order or delayed. How do we ensure the monthly budget is calculated accurately based on when the transaction *occurred*, not when it was *processed*?
* **Technical Solution (Event Time vs. Processing Time):**
    * The system must rely on the `transaction_timestamp` (Event Time) embedded in the Kafka message, rather than the server's current time.
    * Implement **Windowing techniques** (if using Kafka Streams) or handle late-arriving events gracefully by applying updates to the specific historical budget period, triggering a recalculation event if necessary.

### NFR8 - Resilient AI API Integration (Circuit Breakers)
* **Challenge:** Calling external AI APIs (like Gemini/OpenAI) is slow, expensive, and prone to timeouts or rate limits.
* **Technical Solution (Resilience Patterns):**
    * Wrap AI API calls with a **Circuit Breaker pattern** (e.g., using Resilience4j or Jakarta EE Fault Tolerance). If the AI service is down, the system must fallback gracefully (e.g., displaying standard, rule-based text instead of AI generation).
    * Implement an **Asynchronous Request-Reply** pattern: The user clicks "Generate Advice", the backend accepts the request (HTTP 202 Accepted), queues a background job to call the LLM, and pushes the result via WebSocket when ready, preventing blocking HTTP threads.

## 5. Updated Data Flow for a "Categorized Transfer"
1. **Frontend** POSTs transfer request: `from_wallet`, `to_wallet`, `amount`, `category=ENTERTAINMENT`, `idempotency_key`.
2. **Core Wallet Service** processes the synchronous ACID transfer (Locks, Deducts, Commits).
3. Core Service publishes `TransactionCompletedEvent(..., amount, category)` to Kafka topic `transaction-events`.
4. **Fraud Consumer** reads the event -> Checks velocity/volume rules.
5. **PFM Consumer** reads the *same* event simultaneously (Fan-out pattern):
    * Retrieves the user's active budget plan from Redis.
    * Increments the `utilized_amount` for the `ENTERTAINMENT` bucket.
    * Checks if `utilized_amount > threshold`.
    * If yes -> Publishes `BudgetBreachEvent` to Kafka topic `user-notifications`.
6. **Notification Service** reads `BudgetBreachEvent` -> Pushes WebSocket alert to the specific user's app.

## 6. Revised Implementation Roadmap (Adding 3 Weeks)
* **Week 1-8:** (Original Core Wallet, Fraud Engine, and WebSockets setup).
* **Week 9: PFM Data Structures & Aggregation.** Design Budget tables. Implement the CQRS read-model using Redis to track budget utilization without hitting the main DB.
* **Week 10: Event-Driven Alerts & AI Integration.** Build the PFM Kafka Consumer to update budgets and trigger threshold alerts. Integrate the external LLM API with Circuit Breakers for end-of-month analysis.
* **Week 11: PFM Dashboard & Polish.** Extend the Angular frontend with charting (Chart.js/D3) to visualize planned vs. actual spending and display the AI Advisor's insights. Finalize load testing on the CQRS architecture.
