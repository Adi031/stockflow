# StockFlow — Concurrent Inventory & Order Management Platform

A multi-seller commerce platform built as a **backend reliability project**: the interesting part
is not the storefront, it is guaranteeing that inventory is never oversold, orders are never
duplicated, and stock is never leaked when payments fail or checkouts are abandoned.

**Stack:** Java 21 · Spring Boot 3.3 · JPA/Hibernate · PostgreSQL 16 · Redis 7 · Flyway · JWT · React + TypeScript (Vite) · Docker

## Run it

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| API + Swagger UI | http://localhost:8080/swagger-ui.html |
| Postgres / Redis | localhost:5432 / localhost:6379 |

Local dev without Docker for the app: start `postgres` and `redis` via compose, then
`cd backend && mvn spring-boot:run` and `cd frontend && npm install && npm run dev`.

## Demo data

Docker Compose starts the backend with `SEED_ENABLED=true`, so the first boot loads sample data
(`DataSeeder`). It skips itself if any user already exists. All accounts use password **`Password123!`**.

| Role | Login |
|---|---|
| Admin | admin@stockflow.dev |
| Sellers (TechNest, UrbanThreads, HomeHaven) | seller1@stockflow.dev … seller3@stockflow.dev |
| Customers | customer1@stockflow.dev … customer10@stockflow.dev |

Also seeded: 24 products plus one flash-sale item, with varied stock (some low, one out of stock),
six real checkouts in different states (confirmed, shipped, delivered, cancelled), and a live
5-unit flash sale — its ID is printed in the backend log (`Flash sale #N is live`).

Re-seed from scratch: `docker compose down -v && docker compose up --build`.
Running without Docker: set `SEED_ENABLED=true` in the backend's environment.
Turn it off by removing that variable from `docker-compose.yml`.

## Demo script (5 minutes)

1. Register a **Seller** → open a store → create a product with stock **10**.
2. Register a **Customer** → add it to cart → check out with *Force SUCCESS*. Stock drops; order is `CONFIRMED`.
3. Click **Retry same key** → response says `deduplicated: true`, no second order (idempotency).
4. Check out again with *Force FAILED* → order `CANCELLED`, stock returns (compensation).
5. Check out with *Force TIMEOUT* → order stays `PAYMENT_PROCESSING`; after ~2 min the reconciliation job cancels it and releases stock.
6. Register an **Admin** → schedule a flash sale (stock 5) → activate → hammer `/api/flash-sales/{id}/purchase` from many users; successes never exceed 5.

## How the hard problems are solved

| Problem | Mechanism | Where |
|---|---|---|
| Overselling on normal checkout | `SELECT ... FOR UPDATE` (pessimistic row lock) around check-then-decrement, plus DB `CHECK` constraints | `InventoryService`, `InventoryRepository` |
| Concurrent seller edits | Optimistic locking (`@Version`) — low contention, retry is cheap | `Product` |
| Abandoned checkouts locking stock | Reservations with `expires_at`; scheduled sweep returns stock | `ReservationExpiryScheduler` |
| Duplicate orders | Client `Idempotency-Key` + `UNIQUE` column; race loser catches the constraint violation and returns the winner's order | `OrderService`, `V1__init_schema.sql` |
| Payment failure / timeout | Compensating release on failure; reconciliation job for stuck `PAYMENT_PROCESSING` | `OrderService`, `PaymentReconciliationScheduler` |
| Illegal status jumps | Central transition table | `OrderStateMachine` |
| Flash-sale contention | Atomic Redis Lua script (check limit + check stock + decrement); Postgres written only for winners | `flash_sale_purchase.lua`, `FlashSaleService` |
| Lock held during slow payment call | Checkout is deliberately *not* one big transaction; each step is a short transaction | `OrderService` |
| Spring `@Transactional` self-invocation | Transactional steps extracted into separate beans | `OrderTransactionalOps`, `FlashSaleTransactionalOps` |

### What Redis caches — and what it must not
Cached: product detail and search pages (short TTL, evicted on seller edits).
**Never cached:** live `available_stock` on the normal path, order status, payment status —
stale reads there mean selling stock that does not exist. The only stock in Redis is the
flash-sale counter, which is the gatekeeper by design and is seeded from a Postgres carve-out.

## Tests

```bash
cd backend && mvn test        # needs Docker (Testcontainers starts Postgres + Redis)
```

* `ConcurrentCheckoutOversellTest` — 200 threads race for 10 units; asserts exactly 10 succeed and `available + reserved == total`.
* `OrderStateMachineTest` — legal/illegal transitions.

## Known limitations / next steps (be upfront about these in interviews)

* Flash-sale winners are persisted synchronously; at real scale, push wins onto a Redis Stream/outbox and drain with workers. The `outbox_events` table is provisioned but not yet wired.
* If Redis is flushed mid-sale, the counter is lost; recovery would rebuild it from `flash_sale_orders`.
* Multi-item checkout is compensating (release on partial failure), not a single distributed transaction.
* Reservation sweep is polling-based; Redis key-expiry notifications would make it near-instant.
* Refresh tokens are stateless (no rotation/revocation list). Registration lets anyone pick the ADMIN role — **demo convenience only**, lock down before any real use.
* Cart page can't remove single items yet (endpoint exists; UI not wired).
* Not yet built: Gatling load test, seller-scoped order view, Redis-based rate limiting.