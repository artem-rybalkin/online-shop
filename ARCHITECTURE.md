# Online Shop — Full Technical Description

## Overview

A full-stack e-commerce REST API built with Spring Boot 3 and Java 17. It supports a product catalog, session-based shopping carts, JWT-authenticated user accounts, and order management for both registered and guest users. The application is containerized with Docker and ships with a full observability stack: structured logs (Loki/Promtail), metrics (Prometheus/Micrometer), distributed traces (Tempo/OpenTelemetry), and dashboards (Grafana). The frontend is served by a dedicated Nginx container.

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
| Build | Maven (multi-module) |
| Containerization | Docker + Docker Compose |
| Logging | SLF4J + Logback + Logstash encoder → Loki → Grafana |
| Metrics | Micrometer + `micrometer-registry-prometheus` → Prometheus → Grafana |
| Tracing | Micrometer Tracing (`micrometer-tracing-bridge-otel`) + `opentelemetry-exporter-otlp` → Tempo → Grafana |
| AOP | Spring AOP |

---

## Project Structure

```
online-shop/
├── Dockerfile                          # Multi-stage: Maven build → JRE runtime
├── docker-compose.yml                  # All services (see Infrastructure section)
├── pom.xml
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
└── src/
    ├── main/
    │   ├── java/com/shop/
    │   │   ├── OnlineShopApplication.java      # Entry point + seed data
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java          # Filter chain, CORS, auth rules
    │   │   │   └── SwaggerConfig.java           # OpenAPI JWT scheme
    │   │   ├── controller/
    │   │   │   ├── AuthController.java          # POST /api/auth/{register,login}
    │   │   │   ├── ProductController.java       # CRUD + search on /api/products
    │   │   │   ├── CartController.java          # Session cart on /api/cart
    │   │   │   └── OrderController.java         # Orders on /api/orders
    │   │   ├── service/
    │   │   │   ├── UserService.java
    │   │   │   ├── ProductService.java          # @Transactional on writes
    │   │   │   ├── CartService.java
    │   │   │   └── OrderService.java            # BigDecimal total calculation
    │   │   ├── repository/
    │   │   │   ├── UserRepository.java
    │   │   │   ├── ProductRepository.java
    │   │   │   ├── CartItemRepository.java
    │   │   │   └── OrderRepository.java
    │   │   ├── model/
    │   │   │   ├── User.java                    # password @JsonIgnore
    │   │   │   ├── Product.java
    │   │   │   ├── CartItem.java
    │   │   │   ├── Order.java                   # totalAmount: BigDecimal
    │   │   │   └── OrderItem.java               # price: BigDecimal
    │   │   ├── dto/
    │   │   │   ├── AuthRequest.java
    │   │   │   ├── AuthResponse.java
    │   │   │   ├── ProductRequest.java          # @Valid constraints
    │   │   │   ├── CartItemRequest.java
    │   │   │   └── OrderRequest.java            # @Valid constraints (@Email, @NotBlank)
    │   │   ├── security/
    │   │   │   ├── JwtUtil.java                 # Token generation + validation
    │   │   │   ├── JwtFilter.java               # OncePerRequestFilter; catches bad/expired tokens
    │   │   │   ├── AuthRateLimitFilter.java     # Fixed-window rate limiter (10 req/60 s per IP)
    │   │   │   └── CustomUserDetailsService.java
    │   │   └── exception/
    │   │       ├── NotFoundException.java
    │   │       ├── OrderException.java
    │   │       └── GlobalExceptionHandler.java
    │   └── resources/
    │       ├── application.properties
    │       ├── logback-spring.xml               # JSON logs with MDC traceId/spanId
    │       └── db/migration/
    │           ├── V1__initial_schema.sql
    │           ├── V2__add_indexes_and_decimal_money.sql
    │           └── V3__add_constraints.sql
    └── test/
        ├── java/com/shop/
        │   ├── controller/
        │   │   ├── AuthControllerTest.java
        │   │   ├── ProductControllerTest.java
        │   │   ├── CartControllerTest.java
        │   │   └── OrderControllerTest.java
        │   ├── exception/
        │   │   └── GlobalExceptionHandlerTest.java
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
Represents one line in a shopping cart. Linked to a `Product` via FK; scoped to a browser session via a plain `sessionId` string (indexed). There is no User FK — carts are anonymous by design.

### `Order`
The main purchase aggregate. Contains `customerName`, `customerEmail`, `status` (`CHECK status IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED')`), `totalAmount` (**`DECIMAL(10,2)`**), `sessionId` (indexed), `createdAt`, and an optional `user` FK (null for guests). Cascades to `OrderItem` with `orphanRemoval`.

### `OrderItem`
A line item inside an order. Stores a **snapshot** of the product at purchase time: `productName`, `price` (**`DECIMAL(10,2)`**), `quantity` (`CHECK quantity > 0`). No FK back to `Product` — this is intentional so historical orders are unaffected by catalog changes.

---

## API Reference

### Authentication — `/api/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | None | Create account; returns JWT |
| POST | `/api/auth/login` | None | Authenticate; returns JWT |

Both endpoints accept `{ username, email, password }`. Response is `{ token, username, email }`. Duplicate usernames return 400. Bad credentials return 401.

### Products — `/api/products`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/products` | None | List all products |
| GET | `/api/products/{id}` | None | Get product by ID |
| GET | `/api/products/category/{category}` | None | Filter by category |
| GET | `/api/products/search?name=` | None | Case-insensitive name search |
| POST | `/api/products` | Required | Create product |
| DELETE | `/api/products/{id}` | Required | Delete product |

Product writes require a valid JWT. Reads are public. Duplicate product names return 409.

### Cart — `/api/cart`

All cart endpoints are public (no JWT required). A client-generated `sessionId` (typically a UUID) is the sole identity mechanism.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/cart/{sessionId}` | Retrieve all cart items for a session |
| POST | `/api/cart/{sessionId}/add` | Add or increment a product; body: `{ productId, quantity }` |
| DELETE | `/api/cart/item/{cartItemId}` | Remove one item (sessionId via `X-Session-Id` header) |
| DELETE | `/api/cart/{sessionId}/clear` | Empty the entire cart |

Adding an item validates that `stock >= existingCartQuantity + newQuantity`. Attempting to add more than available stock returns 400.

### Orders — `/api/orders`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/orders` | None | Place order from current cart |
| GET | `/api/orders/my` | None | List caller's orders |
| GET | `/api/orders/{id}` | None | Get single order (ownership enforced) |

Order creation body: `{ sessionId, customerName, customerEmail }` — all fields `@NotBlank`, email validated with `@Email`. On success, stock is atomically deducted under a pessimistic write lock and the cart is cleared. If any product lacks sufficient stock, the whole operation rolls back (transactional).

For retrieval, the `sessionId` is read from the `X-Session-Id` request header (preferred, not visible in server logs) with fallback to a `?sessionId=` query param. Ownership is enforced in `OrderService.getOrderById`:
- Authenticated users: `order.user.username` must match the JWT principal.
- Guests: `order.sessionId` must match the session ID from the header/param.
- No match → 403 Forbidden.

---

## Security Architecture

### JWT Flow

```
Client → POST /api/auth/login
       ← { token }

Client → Any request with Authorization: Bearer <token>
       → JwtFilter.doFilterInternal()
           extracts username from token
           validates signature + expiry
           sets UsernamePasswordAuthenticationToken in SecurityContextHolder
       → Controller / Service reads auth from SecurityContextHolder
```

Token settings: HMAC-SHA256, 10-hour expiry. Secret is injected from the `JWT_SECRET` environment variable — no default fallback; the app refuses to start without it.

### Rate Limiting

`AuthRateLimitFilter` (runs before all other filters) applies a fixed-window rate limit of 10 requests per 60-second window per client IP to all `/api/auth/**` endpoints. Returns 429 when exceeded. Expired buckets are evicted on each request to prevent unbounded map growth. IP is resolved from the `X-Forwarded-For` header (first address) with fallback to `RemoteAddr`.

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

Authenticated:
  POST   /api/products/**
  DELETE /api/products/**
  Everything else
```

### Dual-Identity Order Model

Orders can belong to either a registered user or a guest session. `OrderService` reads `SecurityContextHolder` on every operation:
- If principal is not `"anonymousUser"` → look up the `User` entity and attach it to the order.
- Otherwise → the order is guest-only, identified solely by `sessionId`.

Both authenticated users and guests can create, view, and list their own orders. The ownership check in `getOrderById` handles both cases in one method.

### Stateless Session

`SessionCreationPolicy.STATELESS` — the server holds no HTTP session state. Every request must carry its own JWT (or sessionId for guest cart/order access).

---

## Core Business Logic

### Add to Cart (`CartService.addToCart`)
1. Load product (404 if not found).
2. Sum any existing quantity for this product in the session's cart.
3. Check `stock >= existing + requested` (400 if not).
4. If the product is already in the cart, increment its quantity and save; otherwise insert a new `CartItem`.

### Place Order (`OrderService.createOrder`) — `@Transactional`
1. Load all `CartItem`s for the session (400 if cart is empty).
2. For each cart item, acquire a **pessimistic write lock** (`SELECT ... FOR UPDATE`) on the product row.
3. Verify stock ≥ requested quantity (400 if not — transaction rolls back).
4. Decrement stock and save each product.
5. Snapshot cart items into `OrderItem` records (copying name + price at point of purchase).
6. Calculate `totalAmount` using **`BigDecimal` arithmetic** to avoid floating-point rounding errors.
7. Resolve the authenticated user (or leave `null` for guests).
8. Persist the `Order` with all `OrderItem`s.
9. Clear the cart for the session.

### Order Retrieval (`OrderService.getOrderById`)
Single method handles both the ownership check and the data fetch. Throws `AccessDeniedException` (→ 403) if neither username nor sessionId matches the order.

---

## Exception Handling

`GlobalExceptionHandler` (`@ControllerAdvice`) maps exceptions to HTTP responses. All error bodies use the shape `{ timestamp, message }`.

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

Secrets (`MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`, `JWT_SECRET`) are injected via a `.env` file — no default fallbacks; copy `.env.example` and fill in values before first run.

### Database Schema Management

`spring.jpa.hibernate.ddl-auto=none` — Flyway owns all DDL. Three migrations run in order:

| Migration | Content |
|-----------|---------|
| `V1__initial_schema.sql` | Creates all tables |
| `V2__add_indexes_and_decimal_money.sql` | Indexes on `session_id` columns; `DOUBLE` → `DECIMAL(10,2)` for money |
| `V3__add_constraints.sql` | `CHECK` constraints on stock, status, and quantity; `order_id NOT NULL` |

Test profile sets `spring.flyway.enabled=false` and uses H2 with `create-drop`.

### Seed Data
`OnlineShopApplication` registers a `CommandLineRunner` `@Bean` that inserts up to 10 sample products on startup, guarded by `existsByName` to avoid duplicates on restart.

---

## Testing

### Backend — Spring Boot integration tests

72 tests across 7 `@SpringBootTest` / MockMvc classes, all running against H2 in-memory:

| Test class | Tests | Coverage |
|------------|-------|----------|
| `AuthControllerTest` | 6 | Register, login, duplicate username, bad credentials |
| `ProductControllerTest` | 13 | List, get, category, search, create, delete, delete non-existent |
| `CartControllerTest` | 10 | Empty cart, add item, retrieve, remove, clear |
| `OrderControllerTest` | 15 | Create, get by ID, list my orders, X-Session-Id header path, BigDecimal total, stock/cart assertions |
| `GlobalExceptionHandlerTest` | 7 | DB error masking, generic catch-all, 404, 405, 409, 400 validation, 400 malformed JSON |
| `JwtUtilTest` | 10 | Token generation, extraction, validation, expiry, role claim, bad signature |
| `JwtFilterTest` | 4 | No header, non-Bearer header, valid admin JWT, expired JWT |
| `AuthRateLimitFilterTest` | 6 | Rate limiting, 429 response, pass-through, IP extraction, stale entry eviction |

### Frontend — Jest unit tests + Playwright E2E
Located in `frontend-tests/`. Run with `npm test` from the `frontend-tests/` directory.

| Suite | Count | Coverage |
|-------|-------|----------|
| Jest unit (`unit/app.test.js`) | 33 tests | `getImage`, `renderProducts`, `loadProducts`, `applyFilters`, `updateCartUI`, `toggleAuthModal`, `switchAuthTab` |
| Playwright E2E (`e2e/shop.spec.js`) | 60 tests | Page load, category filters, search, cart, checkout, auth, order history, order details, second checkout — Chromium + Firefox |

Jest loads `app.js` into jsdom via `runScripts: 'dangerously'` (`jest.config.js`). The setup file is declared under `setupFilesAfterEnv`.

---

## Key Design Decisions

- **Sessionless identity for guests**: no server-side session is created; the frontend generates and persists a UUID as `sessionId`. Session ID is sent via the `X-Session-Id` request header (not exposed in URLs or server access logs).
- **Order item snapshots**: `OrderItem` stores product name and price at purchase time rather than a FK, so catalog edits never retroactively alter order history.
- **Pessimistic locking for stock**: `SELECT ... FOR UPDATE` in `ProductRepository.findByIdForUpdate` ensures concurrent checkouts for the same product serialize correctly at the DB level.
- **Ownership in the service layer**: `OrderService.getOrderById` is the single enforcement point for order access — no AOP duplication.
- **Generic error responses**: internal exception details stay in server logs; clients receive only a safe, generic message. `User.password` (BCrypt hash) is `@JsonIgnore` so it is never serialised into API responses.
- **No default secrets**: `JWT_SECRET` and `DB_PASSWORD` have no fallback values; the app fails to start if they are not set via environment variables.
- **DECIMAL for money**: `orders.total_amount` and `order_items.price` are stored as `DECIMAL(10,2)` (not `DOUBLE`) to prevent floating-point rounding errors in financial calculations.
- **Fixed-window rate limiting**: `AuthRateLimitFilter` limits auth-endpoint abuse with per-IP buckets stored in a `ConcurrentHashMap`; expired entries are evicted on every request to prevent unbounded memory growth.
