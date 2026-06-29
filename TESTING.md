# Testing Strategy

This project has seven distinct test layers. That's more than a typical project this size needs
on pure merit — several of them were added deliberately to cover the same endpoints from
different tool ecosystems (Java/REST Assured, Node/Playwright, Postman/Newman) so the choice of
black-box tool doesn't become a single point of failure, and so each tool's natural audience
(backend devs, frontend devs, manual/exploratory testers) has a suite in the format they'd
actually reach for. They are not all equally load-bearing — see "Which suite catches what" below
before assuming more layers means more coverage.

## At a glance

| # | Suite | Location | Tool | Tests | Needs a running server? |
|---|---|---|---|---|---|
| 1 | Backend unit/integration | `src/test/java/` | JUnit 5 + MockMvc | 94 | No — H2 in-memory, full Spring context |
| 2 | Frontend unit | `frontend-tests/unit/` | Jest + jsdom | 33 | No — `fetch` mocked |
| 3 | Frontend e2e (browser) | `frontend-tests/e2e/` | Playwright (Chromium + Firefox) | 31 × 2 browsers | Yes |
| 4 | Frontend API | `frontend-tests/api/` | Playwright `request` fixture | 35 | Yes |
| 5 | Black-box API | `api-tests/` | REST Assured (JUnit 5) | 38 | Yes |
| 6 | Postman collection | `postman/` | Postman / Newman | 51 requests, 81 assertions | Yes |
| 7 | Contract tests | `contract-tests/` + `api-tests/.../contract/` | Pact (pact-js + pact-jvm) | 7 interactions | Provider side only |

Suites 3–7 all run against a live, booted instance (the same one — see "Running everything
locally" below). Suites 1–2 are self-contained and need nothing external.

## Which suite catches what

- **MockMvc (1)** is the fast feedback loop for backend logic: validation messages, ownership
  checks, exception mapping, security filter behavior. Runs in seconds, no Docker needed. This is
  where you write a test *while* fixing a bug.
- **Jest (2)** covers pure frontend JS logic (`renderProducts`, `applyFilters`, cart UI updates)
  with `fetch` mocked — it never talks to a real backend, so it can't catch contract drift.
- **Playwright browser e2e (3)** is the only suite that drives real UI and caught real bugs the
  others structurally couldn't: a login payload mismatch between `app.js` and the backend, and a
  test calling a JS function that was never exposed on `window`. If you're choosing one suite to
  trust before a release, it's this one.
- **Playwright API / REST Assured / Postman (4, 5, 6)** are three independent black-box
  implementations of mostly the same HTTP-contract checks (status codes, validation messages,
  ownership, negative paths). They overlap by design — each is "free" reuse of infrastructure a
  different audience already has (frontend devs already run Playwright; Java CI already has
  Maven; manual testers already have Postman installed). Don't expect one to catch something the
  others structurally can't — their value is redundancy and reach, not unique coverage.
- **Pact (7)** is different in kind from the other six: it doesn't check "does the API work," it
  checks "does the API still match what the consumer recorded expecting." It's the one suite that
  fails specifically on *breaking changes*, independent of whether the change is otherwise
  "correct."

## 1. Backend unit/integration tests

```bash
./mvnw test
```

`@SpringBootTest` + `MockMvc`, full Spring context, H2 in-memory database (`application-test.properties`,
`spring.flyway.enabled=false`). No mocking of the service layer — these exercise the real JWT
filter, rate limiter, and JPA layer end to end, just not over a real socket.

| Test class | Tests | Covers |
|---|---|---|
| `AuthControllerTest` | 13 | Register, login (incl. no-email payload matching the real frontend), `/me`, `/logout`, validation |
| `ProductControllerTest` | 20 | CRUD, search, category filter, admin gating, `reset-stock` (incl. negative-value rejection) |
| `CartControllerTest` | 11 | Add/get/remove/clear, stock validation, ownership |
| `OrderControllerTest` | 19 (1 skipped) | Create, ownership (sessionId *and* username paths), stock re-check race, guest→login order merging |
| `GlobalExceptionHandlerTest` | 7 | Error response shape for every mapped exception type |
| `AuthRateLimitFilterTest` | 7 | Rate limiting, IP spoofing resistance, trusted-proxy config |
| `JwtFilterTest` | 4 | Token extraction/validation paths |
| `JwtUtilTest` | 10 | Token generation, expiry, claims |
| `CartServiceTest` | 2 | The `(sessionId, product_id)` unique constraint as a safety net under concurrent adds |
| `OnlineShopApplicationTests` | 1 | Context loads |

The skipped `OrderControllerTest` case is a documented limitation: H2's MVCC doesn't block
concurrent `SELECT FOR UPDATE` between threads, so the pessimistic-lock race test can't run
against H2 — it needs real MySQL (see suite 5's stock-recheck test, which covers the same
scenario against the real database).

## 2. Frontend unit tests

```bash
cd frontend-tests
npm ci
npm run test:unit
```

Jest with `jest-environment-jsdom`; loads `app.js` into jsdom via `runScripts: 'dangerously'`
(`jest.config.js`). Tests pure JS functions with `fetch` mocked — no real backend involved.

## 3. Frontend e2e tests (browser)

```bash
cd frontend-tests
npx playwright install --with-deps chromium firefox
npm run test:e2e   # runs ALL Playwright projects: chromium, firefox, AND api (see #4)
```

Drives a real browser against `http://localhost:8080` (`playwright.config.js`). 31 tests ×
2 browser projects. `retries: 2` is configured deliberately — there's a known flaky pattern around
Firefox's logout-button visibility timing that isn't a real bug, just headless-Firefox timing
variance.

Covers: page load, category filters, search, cart, full checkout flow, auth (login/register/logout),
order history, order details, and a second-checkout-in-the-same-session regression test.

## 4. Frontend API tests

Same Playwright config, same `npm run test:e2e` command — added as a third project (`api`) in
`playwright.config.js` pointing at `frontend-tests/api/` instead of `frontend-tests/e2e/`. Uses
Playwright's `request` fixture: no browser, no `page`, just HTTP calls. Each test gets an isolated
`APIRequestContext` (and cookie jar) by default, so tests don't leak auth state into each other —
unlike the Postman collection (suite 6), which shares one cookie jar across the whole run and
needs explicit logout steps to avoid leaking an admin session into "guest" requests.

To run only this project: `npx playwright test --project=api` from `frontend-tests/`.

## 5. Black-box API tests (REST Assured)

```bash
cd api-tests
mvn test -Dapi.baseUrl=http://localhost:8080   # defaults to this URL if omitted
```

A standalone Maven module — **not** part of the root `pom.xml`'s reactor — because it makes real
HTTP calls against a running instance and has nothing to do with the Spring test context used by
suite 1. See `api-tests/README.md`.

| Test class | Tests | Covers |
|---|---|---|
| `HealthApiTest` | 1 | `/actuator/health` |
| `AuthApiTest` | 8 | Register/login/me/logout flow, validation, malformed JSON, wrong HTTP method |
| `ProductApiTest` | 13 | CRUD, admin gating, validation (price/stock/name), `reset-stock` incl. negative-value rejection |
| `CartApiTest` | 8 | Add/remove, validation, stock checks, ownership |
| `OrderApiTest` | 8 | Create/get, validation, the stock-recheck race condition (against **real MySQL**, where the pessimistic lock actually blocks — this is the test suite 1 has to skip) |

`ApiTestSupport.productWithStock(minStock)` exists because this suite runs against a real,
*persistent* dev database that earlier test runs (including the Playwright suite) have already
been adding to carts and placing orders against — "the first product" can't be assumed to have
stock. See `POST /api/products/reset-stock` below.

## 6. Postman collection

```bash
cd postman
npm ci
npm run test:api
```

Same endpoint coverage as suite 5, in Postman Collection v2.1 format, runnable via the Postman
app (import `online-shop.postman_collection.json` + the `Local` environment) or headlessly via
Newman. Folders run in order: **Setup** (admin login, `reset-stock`, forbidden/negative-value
checks) → **Auth** → **Products** → **Cart** → **Orders**.

Two things this suite had to handle that the others didn't, because Postman/Newman shares one
cookie jar across the entire run (unlike Playwright's per-test isolation or REST Assured's
explicit per-call cookies):
- An admin session logged in for the Products folder's admin-gated tests was silently leaking
  into the Cart/Orders folders' "guest" requests, attributing guest orders to admin and breaking
  an ownership test. Fixed with an explicit logout at the end of the Products folder.
- A test asserted a logged-out cookie reads back as `''`. A real cookie jar deletes a cookie
  outright on `Max-Age=0` rather than storing an empty value — the assertion now checks for
  `undefined`.

## 7. Contract tests (Pact)

**Consumer side** — `contract-tests/consumer/`:

```bash
cd contract-tests/consumer
npm ci
npm run test:pact
```

Records what the frontend expects from 4 endpoints (login success/failure, `/me`, products list,
a 404, adding to cart, an ownership-forbidden order lookup) using pact-js's mock provider, and
writes the contract to `contract-tests/pacts/OnlineShopFrontend-OnlineShopAPI.json`. The committed
file is the source of truth for the provider side; regenerate and commit it whenever the
frontend's expectations of the API change.

**Provider side** — `api-tests/src/test/java/com/shop/apitests/contract/ProviderPactVerificationTest.java`:

```bash
cd api-tests
mvn test -Dtest=ProviderPactVerificationTest
```

Lives in `api-tests` (not `src/test/java`) because, like the rest of that module, it needs a real
running server — it replays every recorded interaction against the live API and checks the
response actually matches. Uses `pact-jvm-provider-junit5`.

**Known limitation**: the "order 1 exists and belongs to a different session" provider state
assumes order id `1` already exists (true for this repo's accumulated dev database). A genuinely
fresh database needs one order placed before that specific interaction can verify — the state
handler doesn't create it itself, since the pact interaction's request path hardcodes
`/api/orders/1`.

## The `reset-stock` endpoint

`POST /api/products/reset-stock?stock=9999` (admin-only, defaults to 9999) sets every product's
stock to the given value in one bulk update. It exists purely to support suites 4–7: they all run
against the same persistent dev database, and without it, enough cumulative test runs deplete
whichever product happens to be "first," causing unrelated tests to fail with "insufficient
stock" for reasons that have nothing to do with what they're actually testing. Rejects negative
values the same way product creation does.

## Running everything locally

```bash
# 1. Start the real stack
docker compose up -d

# 2. Fast in-process suites — no server needed
./mvnw test
cd frontend-tests && npm ci && npm run test:unit && cd ..

# 3. Generate the consumer contract fresh
cd contract-tests/consumer && npm ci && npm run test:pact && cd ../..

# 4. Black-box suites against the running stack
cd frontend-tests && npx playwright install --with-deps chromium firefox && npm run test:e2e && cd ..
cd api-tests && mvn test -Dapi.baseUrl=http://localhost:8080 && cd ..
cd postman && npm ci && npm run test:api && cd ..
```

This is the same sequence [CI.md](CI.md) runs, just without the ephemeral MySQL service
container — against `docker compose up`'s persistent one instead.
