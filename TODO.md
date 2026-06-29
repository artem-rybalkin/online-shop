# TODO

---

## ✅ Test Coverage — Completed

All test gaps identified in the QE analysis have been closed.

| # | What was missing | Implemented in |
|---|-----------------|----------------|
| 1 | `DELETE /api/products/{id}` on non-existent ID → 204 | `ProductControllerTest` · `deleteProduct_ShouldReturnNoContent_EvenWhenProductDoesNotExist` |
| 2 | `GET /api/orders/my` for authenticated user | `OrderControllerTest` · `getMyOrders_ShouldReturnOrders_ForAuthenticatedUser` |
| 3 | `handleDatabaseError` returns generic message, not raw SQL | `GlobalExceptionHandlerTest` · `handleDatabaseError_ShouldReturnGenericMessage_NotRawSqlDetails` |
| 4 | `handleAll` returns generic message, not stack trace | `GlobalExceptionHandlerTest` · `handleAll_ShouldReturnGenericMessage_NotRawExceptionDetails` |

---

## ✅ Observability

### 1 · JVM Monitoring
Expose JVM metrics (heap usage, GC pauses, thread count, class loading) via **Micrometer + Spring Boot Actuator** and visualise them in Grafana.

### 2 · Resource Monitoring
Track host/container **CPU, memory, disk I/O, and network** via Prometheus + Node Exporter. Add dedicated Grafana dashboards.

---

## ⬜ Performance Testing

### 3 · Identify Playwright flows for reuse in perf tests
Review `frontend-tests/e2e/shop.spec.js`. Good candidates: **add-to-cart**, **guest checkout**, **login**, **product browse**. Extract shared user-journey helpers usable in k6 or Gatling scripts.

### ✅ 4 · Set product stock to unlimited before perf run
Done — `POST /api/products/reset-stock?stock=9999` (admin-only) bulk-sets every product's stock. Originally added to unblock the black-box test suites (see [TESTING.md](TESTING.md)), which hit the exact same "shared seed data gets depleted" problem this perf-test item describes. Reusable as-is for a perf run.

### 5 · Restore product stock after perf run
Snapshot stock values before the run and restore them afterwards. Implement as a paired `before-perf.sql` / `after-perf.sql` or a Spring Boot admin endpoint.

### 6 · Prepare perf test datasets
Create representative datasets: realistic user accounts, varied cart sizes, order history. Script insertion and teardown so every perf run starts from the same baseline.

---

## ✅ UI Service

### 7 · Extract UI into a separate service
Move `index.html`, `app.js`, and `style.css` into a standalone service — e.g. a **Vite / React** or **Next.js** app, or a dedicated **Nginx container** — decoupled from the Spring Boot backend. Add to `docker-compose.yml` with its own port.

### 8 · Add distributed tracing across the whole app
Instrument every layer with **OpenTelemetry**:
- **Spring Boot backend** — `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`; auto-instruments HTTP requests and DB queries; exports traces via OTLP to **Tempo**
- **Infrastructure** — correlate traces with Loki logs using `traceId`/`spanId` injected via Logback MDC; Grafana Tempo datasource wired with trace→log and trace→metrics links
