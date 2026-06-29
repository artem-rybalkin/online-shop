# Online Shop — Full Technical Description

## Overview

A full-stack e-commerce REST API built with Spring Boot 3 and Java 17. It supports a product catalog, session-based shopping carts, JWT-authenticated user accounts, and order management for both registered and guest users. The application is containerized with Docker and ships with a full observability stack: structured logs (Loki/Promtail), metrics (Prometheus/Micrometer), distributed traces (Tempo/OpenTelemetry), and dashboards (Grafana). The frontend is served by a dedicated Nginx container.

See [README.md](README.md) for the quick start, [TESTING.md](TESTING.md) for the seven test layers, and [CI.md](CI.md) for the GitHub Actions pipeline.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 17 |
| Framework | Spring Boot 3.4.2 |
| Security | Spring Security + JJWT 0.12.5 + fixed-window rate limiter |
| Persistence | Spring Data JPA + Hibernate |
| Database | MySQL 8.0 (H2 for tests) |
| Connection pool | HikariCP (max 10 connections) |
| API docs | Springdoc OpenAPI / Swagger UI |
| Validation | Jakarta Bean Validation |
| Boilerplate | Lombok |
| Build | Maven (`api-tests/` is a standalone second module, not part of the root reactor) |
| Containerization | Docker + Docker Compose |
| Logging | SLF4J + Logback + Logstash encoder → Loki → Grafana |
| Metrics | Micrometer + `micrometer-registry-prometheus` → Prometheus → Grafana |
| Tracing | Micrometer Tracing (`micrometer-tracing-bridge-otel`) + `opentelemetry-exporter-otlp` → Tempo → Grafana |
| AOP | Spring AOP |
| Black-box/contract testing | REST Assured, Postman/Newman, Playwright (`request` fixture), Pact (pact-js + pact-jvm) — see [TESTING.md](TESTING.md) |

---

## Project Structure

```
online-shop/
├── Dockerfile                          # Multi-stage: Maven build → JRE runtime
├── docker-compose.yml                  # All services (see Infrastructure section)
├── pom.xml
├── README.md                           # Quick start
├── ARCHITECTURE.md                     # This file
├── TESTING.md                          # Full testing strategy (7 layers)
├── CI.md                               # GitHub Actions pipeline explained
├── .github/workflows/ci.yml            # CI: backend-tests job → e2e-tests job
├── prometheus.yml                      # Prometheus scrape config (app + node-exporter)
├── tempo.yml                           # Grafana Tempo config (OTLP ingest, local storage)
├── promtail-config.yml                 # Docker log shipping to Loki
├── grafana/
│   └── provisioning/
│       ├── datasources/
│       │   └── datasources.yml         # Auto-provisions Prometheus, Loki, Tempo
│       └── dashboards/
│           ├── dashboards.yml          # Dashboard file provider config
│           ├── jvm.json                # JVM heap, GC, threads, CPU, HTTP rate
│           └── node.json               # Host CPU, memory, disk I/O, network I/O
├── .env.example                        # Required env vars template (never commit .env)
├── ui/
│   ├── Dockerfile                      # nginx:alpine; copies static files from src/
│   └── nginx.conf                      # Gzip, security headers, cache; proxies /api/ → app:8080
├── frontend-tests/                     # Jest unit + Playwright (browser e2e + API) — see TESTING.md
├── api-tests/                          # Standalone REST Assured suite + Pact provider verification — see TESTING.md
├── postman/                            # Postman collection + Newman runner — see TESTING.md
├── contract-tests/                     # Pact consumer suite + generated contract — see TESTING.md
└── src/
    ├── main/
    │   ├── java/com/shop/
    │   │   ├── OnlineShopApplication.java      # Entry point + seed data
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java          # Filter chain, CORS, auth rules
    │   │   │   └── SwaggerConfig.java           # OpenAPI JWT scheme
    │   │   ├── controller/
    │   │   │   ├── AuthController.java          # POST /api/auth/{register,login,logout}, GET /me
    │   │   │   ├── ProductController.java       # CRUD + search + reset-stock on /api/products
    │   │   │   ├── CartController.java          # Session cart on /api/cart
    │   │   │   └── OrderController.java         # Orders on /api/orders
    │   │   ├── service/
    │   │   │   ├── UserService.java             # Lookup/registration, used by AuthController
    │   │   │   ├── ProductService.java          # @Transactional on writes; bulk resetAllStock
    │   │   │   ├── CartService.java
    │   │   │   └── OrderService.java            # BigDecimal total calculation
    │   │   ├── repository/
    │   │   │   ├── UserRepository.java
    │   │   │   ├── ProductRepository.java       # findByIdForUpdate (pessimistic lock), updateAllStock
    │   │   │   ├── CartItemRepository.java
    │   │   │   └── OrderRepository.java
    │   │   ├── model/
    │   │   │   ├── User.java                    # password @JsonIgnore
    │   │   │   ├── Product.java
    │   │   │   ├── CartItem.java                # unique (sessionId, product_id)
    │   │   │   ├── Order.java                   # totalAmount: BigDecimal
    │   │   │   └── OrderItem.java               # price: BigDecimal
    │   │   ├── dto/
    │   │   │   ├── AuthRequest.java              # register payload (username, password, email)
    │   │   │   ├── LoginRequest.java             # login payload (username, password only)
    │   │   │   ├── AuthResponse.java
    │   │   │   ├── ProductRequest.java          # @Valid constraints
    │   │   │   ├── CartItemRequest.java
    │   │   │   └── OrderRequest.java            # @Valid constraints (@Email, @NotBlank)
    │   │   ├── security/
    │   │   │   ├── JwtUtil.java                 # Token generation + validation
    │   │   │   ├── JwtFilter.java               # OncePerRequestFilter; catches bad/expired tokens
    │   │   │   ├── AuthRateLimitFilter.java     # Fixed-window rate limiter; trusted-proxy gated
    │   │   │   ├── SecurityUtils.java           # Shared "current username, anonymous → null" helper
    │   │   │   └── CustomUserDetailsService.java
    │   │   └── exception/
    │   │       ├── NotFoundException.java
    │   │       ├── OrderException.java
    │   │       ├── ErrorResponse.java           # Shared {timestamp, message} body builder
    │   │       └── GlobalExceptionHandler.java
    │   └── resources/
    │       ├── application.properties
    │       ├── logback-spring.xml               # JSON logs with MDC traceId/spanId
    │       └── db/migration/
    │           ├── V1__initial_schema.sql
    │           ├── V2__add_indexes_and_decimal_money.sql
    │           ├── V3__add_constraints.sql
    │           └── V4__cart_items_unique_session_product.sql
    └── test/
        ├── java/com/shop/
        │   ├── controller/
        │   │   ├── AuthControllerTest.java
        │   │   ├── ProductControllerTest.java
        │   │   ├── CartControllerTest.java
        │   │   └── OrderControllerTest.java
        │   ├── exception/
        │   │   └── GlobalExceptionHandlerTest.java
        │   ├── service/
        │   │   └── CartServiceTest.java
        │   └── security/
        │       ├── JwtUtilTest.java
        │       ├── JwtFilterTest.java
        │       └── AuthRateLimitFilterTest.java
        └── resources/
            └── application-test.properties      # H2 in-memory, Flyway disabled
```

---

## Data Model

### Entities & Relationships

```
User (1) ──────────────────────────── (N) Order
                                            │
                                           (1)
                                            │
                                           (N) OrderItem ──── (snapshot) Product name/price

CartItem ──── Product
  │
  └─ sessionId (string, ties cart to browser session)
```

### `User`
Stores registered accounts. Has a `username` (login), `email`, and BCrypt-hashed `password`. Linked to orders via a one-to-many relationship.

### `Product`
The catalog. Fields: `id`, `name` (unique), `description`, `price` (Double), `stock` (Integer, `CHECK stock >= 0`), `category`. No FK relationships — products are referenced by value in `OrderItem` and by FK in `CartItem`.

### `CartItem`
Represents one line in a shopping cart. Linked to a `Product` via FK; scoped to a browser session via a plain `sessionId` string (indexed). There is no User FK — carts are anonymous by design. A unique constraint on `(sessionId, product_id)` (V4 migration) prevents duplicate rows from concurrent `addToCart` calls for the same product.

### `Order`
The main purchase aggregate. Contains `customerName`, `customerEmail`, `status` (`CHECK status IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED')`), `totalAmount` (**`DECIMAL(10,2)`**), `sessionId` (indexed), `createdAt`, and an optional `user` FK (null for guests). Cascades to `OrderItem` with `orphanRemoval`.

### `OrderItem`
A line item inside an order. Stores a **snapshot** of the product at purchase time: `productName`, `price` (**`DECIMAL(10,2)`**), `quantity` (`CHECK quantity > 0`). No FK back to `Product` — this is intentional so historical orders are unaffected by catalog changes.

---

## API Reference

### Authentication — `/api/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | None | Create account; body `{ username, password, email }`; sets a `jwt` httpOnly cookie |
| POST | `/api/auth/login` | None | Authenticate; body `{ username, password }` **only** — deliberately a separate `LoginRequest` DTO from register's `AuthRequest`, since the real frontend never sends an email on login |
| GET | `/api/auth/me` | Cookie | Current user; 401 if not authenticated |
| POST | `/api/auth/logout` | None | Expires the `jwt` cookie |

Both register and login respond with `{ username, email }` plus a `Set-Cookie: jwt=...; HttpOnly`. Duplicate usernames return 400. Bad credentials return 401.

### Products — `/api/products`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/products` | None | List all products (paginated) |
| GET | `/api/products/{id}` | None | Get product by ID |
| GET | `/api/products/category/{category}` | None | Filter by category |
| GET | `/api/products/search?name=` | None | Case-insensitive name search |
| POST | `/api/products` | `ROLE_ADMIN` | Create product |
| DELETE | `/api/products/{id}` | `ROLE_ADMIN` | Delete product |
| POST | `/api/products/reset-stock?stock=9999` | `ROLE_ADMIN` | Bulk-set every product's stock (defaults to 9999); rejects negative values |

Product writes require an admin JWT (not just any authenticated user — see Security Rules below). Reads are public. Duplicate product names return 400. `reset-stock` exists for the test suites in [TESTING.md](TESTING.md), which run repeatedly against the same persistent dev database and would otherwise deplete shared seed-data stock.

### Cart — `/api/cart`

All cart endpoints are public (no JWT required). A client-generated `sessionId` (typically a UUID) is the sole identity mechanism, preferably sent via the `X-Session-Id` header.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/cart/{sessionId}` | Retrieve all cart items for a session |
| POST | `/api/cart/{sessionId}/add` | Add or increment a product; body: `{ productId, quantity }` |
| DELETE | `/api/cart/item/{cartItemId}` | Remove one item (sessionId via `X-Session-Id` header or `?sessionId=`; 400 if neither is supplied) |
| DELETE | `/api/cart/{sessionId}/clear` | Empty the entire cart |

Adding an item validates that `stock >= existingCartQuantity + newQuantity`. Attempting to add more than available stock returns 400.

### Orders — `/api/orders`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/orders` | None | Place order from current cart |
| GET | `/api/orders/my` | None | List caller's orders (by JWT username, sessionId, or both — see below) |
| GET | `/api/orders/{id}` | None | Get single order (ownership enforced) |

Order creation body: `{ sessionId, customerName, customerEmail }` — all fields `@NotBlank`, email validated with `@Email`. On success, stock is atomically deducted under a pessimistic write lock and the cart is cleared. If any product lacks sufficient stock, the whole operation rolls back (transactional).

For retrieval, the `sessionId` is read from the `X-Session-Id` request header (preferred, not visible in server logs) with fallback to a `?sessionId=` query param. Ownership is enforced in `OrderService.getOrderById` using an **OR**: username match *or* sessionId match grants access. `getOrdersForCurrentUser` (`/my`) does the same union — when both a JWT and a sessionId are present, it returns orders matching *either*, so a guest order placed before login still shows up after the user logs in (as long as the same sessionId is still sent). No match on either → 403 Forbidden.

---

## Security Architecture

### JWT Flow

```
Client → POST /api/auth/login
       ← Set-Cookie: jwt=...; HttpOnly

Client → Any request (cookie sent automatically by the browser)
       → JwtFilter.doFilterInternal()
           extracts username from token
           validates signature + expiry
           sets UsernamePasswordAuthenticationToken in SecurityContextHolder
       → Controller / Service reads auth via SecurityUtils.getCurrentUsername()
```

Token settings: HMAC-SHA256, 10-hour expiry. Secret is injected from the `JWT_SECRET` environment variable — no default fallback; the app refuses to start without it.

### Rate Limiting

`AuthRateLimitFilter` (runs before all other filters) applies a fixed-window rate limit (default 10 requests per 60-second window per client IP, configurable via `RATE_LIMIT_AUTH_MAX`/`RATE_LIMIT_AUTH_WINDOW`) to all `/api/auth/**` endpoints. Returns 429 when exceeded. Expired buckets are evicted on each request to prevent unbounded map growth.

IP resolution is **trusted-proxy gated**: `X-Forwarded-For` is only honored when the request's actual remote address is in the configured `rate-limit.auth.trusted-proxies` allowlist (`RATE_LIMIT_AUTH_TRUSTED_PROXIES` env var). From any other address, the header is ignored and the raw connection IP is used — otherwise a client could spoof a fresh `X-Forwarded-For` value on every request and bypass the limit entirely.

### Security Rules (SecurityConfig)

```
Public (no JWT):
  /api/auth/**
  GET  /api/products/**
  /api/cart/**
  POST /api/orders
  GET  /api/orders/my
  GET  /api/orders/{id}
  /swagger-ui/**, /v3/api-docs/**
  /actuator/health, /actuator/info, /actuator/prometheus
  Static resources

ROLE_ADMIN required:
  POST   /api/products/**   (includes /reset-stock)
  DELETE /api/products/**

Authenticated (any role):
  Everything else
```

### Dual-Identity Order Model

Orders can belong to either a registered user or a guest session. `OrderService` calls `SecurityUtils.getCurrentUsername()` — the single shared helper for "resolve the logged-in username, treating Spring Security's `anonymousUser` principal as not-logged-in" — on every operation:
- Non-null username → look up the `User` entity and attach it to the order.
- Otherwise → the order is guest-only, identified solely by `sessionId`.

Both authenticated users and guests can create, view, and list their own orders. The ownership check in `getOrderById` (and the listing logic in `getOrdersForCurrentUser`) handles both cases via an OR/union, so a guest order survives the guest logging in later (see API Reference above).

### Stateless Session

`SessionCreationPolicy.STATELESS` — the server holds no HTTP session state. Every request must carry its own JWT cookie (or sessionId for guest cart/order access).

---

## Core Business Logic

### Add to Cart (`CartService.addToCart`)
1. Load product (404 if not found), locking its row (`findByIdForUpdate`) for the duration of the check — serializes concurrent adds of the same product.
2. Look up any existing cart row for this `(sessionId, productId)` pair.
3. Check `stock >= existing + requested` (400 if not).
4. If the product is already in the cart, increment its quantity and save; otherwise insert a new `CartItem`. A DB-level unique constraint on `(sessionId, product_id)` is a second line of defense if the lock is ever bypassed.

### Place Order (`OrderService.createOrder`) — `@Transactional`
1. Load all `CartItem`s for the session (400 if cart is empty).
2. For each cart item, acquire a **pessimistic write lock** (`SELECT ... FOR UPDATE`) on the product row.
3. Verify stock ≥ requested quantity (400 if not — transaction rolls back). This re-check is independent of `addToCart`'s own check and is what actually catches a stock drop that happens *between* adding to cart and checking out.
4. Decrement stock and save each product.
5. Snapshot cart items into `OrderItem` records (copying name + price at point of purchase).
6. Calculate `totalAmount` using **`BigDecimal` arithmetic** to avoid floating-point rounding errors.
7. Resolve the authenticated user via `SecurityUtils.getCurrentUsername()` (or leave `null` for guests).
8. Persist the `Order` with all `OrderItem`s.
9. Clear the cart for the session.

### Order Retrieval (`OrderService.getOrderById` / `getOrdersForCurrentUser`)
Ownership is username-OR-sessionId, not either/or exclusively — see API Reference above. Throws `AccessDeniedException` (→ 403) if neither matches.

### Bulk Stock Reset (`ProductService.resetAllStock`)
A single `UPDATE products SET stock = :stock` (no per-row fetch) — see the `reset-stock` endpoint in API Reference. Test-and-dev-only utility, not used by any user-facing flow.

---

## Exception Handling

`GlobalExceptionHandler` (`@ControllerAdvice`) maps exceptions to HTTP responses. All error bodies use the shape `{ timestamp, message }`, built by the shared `ErrorResponse.body(message)` helper — also reused by `AuthRateLimitFilter`'s 429 response, which runs before `DispatcherServlet` and so can't go through `@ControllerAdvice` itself.

| Exception | HTTP Status |
|-----------|-------------|
| `NotFoundException` | 404 |
| `NoResourceFoundException` | 404 |
| `OrderException` | 400 |
| `MethodArgumentNotValidException` | 400 (first field error message) |
| `HttpMessageNotReadableException` | 400 ("Malformed or missing request body") |
| `AuthenticationException` | 401 |
| `AccessDeniedException` | 403 |
| `HttpRequestMethodNotSupportedException` | 405 |
| `DataIntegrityViolationException` | 409 |
| `DataAccessException` | 500 (generic message; detail in server log) |
| `Exception` (catch-all) | 500 (generic message; detail in server log) |

Internal error details (SQL text, stack traces, table names) are logged server-side only and never returned to the client.

---

## Observability Stack (Docker Compose)

```
App container
    │
    ├─ stdout JSON logs (Logstash encoder + MDC traceId/spanId)
    │       ↓
    │   Promtail → Loki → Grafana (log explorer)
    │
    ├─ /actuator/prometheus (Micrometer registry)
    │       ↓
    │   Prometheus → Grafana (JVM + HTTP dashboards)
    │
    └─ OTLP HTTP :4318 (Micrometer Tracing → OTel bridge)
            ↓
        Tempo → Grafana (trace explorer)
                    │ trace→log correlation (traceId in Loki)
                    │ trace→metrics links (Prometheus)

Node Exporter → Prometheus → Grafana (host CPU / memory / disk / network dashboard)
```

All Grafana datasources (Prometheus, Loki, Tempo) and dashboards are provisioned automatically from `grafana/provisioning/` on container start — no manual setup required. Trace-to-log correlation is configured to query `{container="shop-app"} | json | traceId="<id>"` in Loki.

---

## Infrastructure

### Dockerfile (multi-stage)
- **Stage 1**: `maven:3.8.5-openjdk-17` — runs `mvn package -DskipTests`, producing a fat JAR.
- **Stage 2**: `eclipse-temurin:17-jre-jammy` — copies only the JAR; runs as non-root user `appuser`; JVM capped at `-Xmx512m`.

### docker-compose.yml Services

All services have `restart: unless-stopped`. Prometheus, Tempo, Grafana, and UI nginx have healthchecks. Named volumes persist data across restarts: `mysql_data`, `prometheus_data`, `tempo_data`, `grafana_data`, `promtail_data`.

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| `db` | `mysql:8.0` | 3306 | Primary datastore |
| `app` | Built from `Dockerfile` | 8080 | Spring Boot — compiled JAR, non-root |
| `ui` | Built from `ui/Dockerfile` | 8081 | Nginx — gzip, security headers, proxies `/api/` |
| `loki` | `grafana/loki` | 3100 | Log aggregation backend |
| `promtail` | `grafana/promtail` | — | Ships Docker container logs to Loki; positions on named volume |
| `prometheus` | `prom/prometheus` | 9090 | Metrics scraper |
| `node-exporter` | `prom/node-exporter` | 9100 | Host/VM resource metrics |
| `tempo` | `grafana/tempo` | 3200 / 4317 / 4318 | Distributed trace storage (OTLP ingest) |
| `grafana` | `grafana/grafana` | 3030 | Dashboards (auto-provisioned) |

Secrets (`MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`, `JWT_SECRET`) are injected via a `.env` file — no default fallbacks; copy `.env.example` and fill in values before first run. The `app` service's `environment:` block also passes through `RATE_LIMIT_AUTH_MAX`, `RATE_LIMIT_AUTH_WINDOW`, and `COOKIE_SECURE` (each with the same defaults `application.properties` itself has) — these are genuinely optional, but they only take effect under docker-compose if listed here, which `.env.example` had documented for a while before the pass-through itself was added.

### Database Schema Management

`spring.jpa.hibernate.ddl-auto=none` — Flyway owns all DDL. Four migrations run in order:

| Migration | Content |
|-----------|---------|
| `V1__initial_schema.sql` | Creates all tables |
| `V2__add_indexes_and_decimal_money.sql` | Indexes on `session_id` columns; `DOUBLE` → `DECIMAL(10,2)` for money |
| `V3__add_constraints.sql` | `CHECK` constraints on stock, status, and quantity; `order_id NOT NULL` |
| `V4__cart_items_unique_session_product.sql` | Unique constraint on `cart_items(session_id, product_id)` |

Test profile sets `spring.flyway.enabled=false` and uses H2 with `create-drop`.

**Operational note**: `V4` only succeeds if no duplicate `(session_id, product_id)` rows already exist. On an existing database with pre-fix duplicate rows (a real possibility, since the duplicates are exactly what V4 fixes), the migration fails and must be repaired manually — merge the duplicates (sum quantities, delete extras), then delete the failed `flyway_schema_history` row before restarting. Never edit an already-applied migration's SQL to fix this after the fact; Flyway checksums it.

### Seed Data
`OnlineShopApplication` registers a `CommandLineRunner` `@Bean` (skipped under the `test` profile) that creates an `admin`/`admin` account and inserts sample products on startup, guarded by `existsByName` to avoid duplicates on restart.

---

## Testing

Seven layers — backend MockMvc, frontend Jest, Playwright (browser e2e + API), REST Assured, Postman/Newman, and Pact contract tests. Full breakdown, what each one catches, and how to run them: **[TESTING.md](TESTING.md)**.

| Layer | Tests | Needs a running server? |
|---|---|---|
| Backend MockMvc (`src/test/java/`) | 94 | No |
| Frontend Jest unit (`frontend-tests/unit/`) | 33 | No |
| Playwright browser e2e (`frontend-tests/e2e/`) | 31 × 2 browsers | Yes |
| Playwright API (`frontend-tests/api/`) | 35 | Yes |
| REST Assured (`api-tests/`) | 38 | Yes |
| Postman/Newman (`postman/`) | 51 requests, 81 assertions | Yes |
| Pact contract (`contract-tests/` + `api-tests/.../contract/`) | 7 interactions | Provider side only |

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs `backend-tests` (the fast in-process suite) on every push/PR, then `e2e-tests` (everything else: real MySQL, the built jar, and all six remaining test layers) once that passes. Full breakdown: **[CI.md](CI.md)**.

---

## Key Design Decisions

- **Sessionless identity for guests**: no server-side session is created; the frontend generates and persists a UUID as `sessionId`. Session ID is sent via the `X-Session-Id` request header (not exposed in URLs or server access logs).
- **Order item snapshots**: `OrderItem` stores product name and price at purchase time rather than a FK, so catalog edits never retroactively alter order history.
- **Pessimistic locking for stock**: `SELECT ... FOR UPDATE` in `ProductRepository.findByIdForUpdate` ensures concurrent checkouts (and concurrent cart adds) for the same product serialize correctly at the DB level; a unique DB constraint on `cart_items(session_id, product_id)` backs that up in case the lock is ever bypassed.
- **Ownership in the service layer**: `OrderService` is the single enforcement point for order access — no AOP duplication, and ownership is username-OR-sessionId so a guest order isn't orphaned when the guest later logs in.
- **One shared "current username" helper**: `SecurityUtils.getCurrentUsername()` replaced four separate inline copies of "resolve username, treat anonymousUser as null" that had quietly drifted (some checked `isAuthenticated()`, some didn't).
- **Generic error responses**: internal exception details stay in server logs; clients receive only a safe, generic message, built from one shared `ErrorResponse` helper used by both `@ControllerAdvice` and the raw servlet filter that runs before it. `User.password` (BCrypt hash) is `@JsonIgnore` so it is never serialised into API responses.
- **No default secrets**: `JWT_SECRET` and `DB_PASSWORD` have no fallback values; the app fails to start if they are not set via environment variables.
- **DECIMAL for money**: `orders.total_amount` and `order_items.price` are stored as `DECIMAL(10,2)` (not `DOUBLE`) to prevent floating-point rounding errors in financial calculations.
- **Fixed-window rate limiting, spoofing-resistant**: `AuthRateLimitFilter` limits auth-endpoint abuse with per-IP buckets stored in a `ConcurrentHashMap`, evicted on every request to bound memory growth; `X-Forwarded-For` is only trusted from a configured allowlist of reverse proxies, not from arbitrary clients.
- **A dedicated test/dev endpoint for stock**: `POST /api/products/reset-stock` exists because several test suites run repeatedly against the same persistent dev database — without it, accumulated stock depletion from earlier runs causes unrelated test failures. It's admin-gated and validated the same way product creation is (no negative stock), but it's explicitly a test-support endpoint, not part of any user-facing flow.
