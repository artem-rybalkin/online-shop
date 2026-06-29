# Online Shop

A full-stack e-commerce REST API: product catalog, session-based guest carts, JWT-authenticated
accounts, and order management — built with Spring Boot 3 / Java 17 / MySQL, with a static JS
frontend, a full observability stack (Grafana/Prometheus/Loki/Tempo), and seven layers of
automated testing.

For the deep technical write-up (data model, security architecture, business logic, design
decisions) see **[ARCHITECTURE.md](ARCHITECTURE.md)**. For the testing strategy see
**[TESTING.md](TESTING.md)**. For the CI pipeline see **[CI.md](CI.md)**.

## Quick start

```bash
cp .env.example .env
# fill in MYSQL_ROOT_PASSWORD, DB_PASSWORD, JWT_SECRET (see comments in .env.example)

docker compose up --build
```

Then open:
- **App + UI**: http://localhost:8080 (the Spring Boot app serves the static frontend directly)
- **UI via nginx**: http://localhost:8081 (same frontend, fronted by the `ui` container)
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Grafana** (logs/metrics/traces): http://localhost:3030

A seed `CommandLineRunner` creates an `admin` / `admin` account and ~10 sample products on first
boot (skipped under the `test` Spring profile).

## Running without the full stack

For faster iteration, run just the app against a local MySQL instead of the whole compose stack:

```bash
docker run -d --name shop-mysql -e MYSQL_ROOT_PASSWORD=<DB_PASSWORD from .env> -e MYSQL_DATABASE=shop_db -p 3306:3306 mysql:8.0
./mvnw spring-boot:run
```

`application.properties` defaults `DB_HOST` to `localhost`, so no extra env vars are needed beyond
what's already in `.env`.

## Repository layout

| Path | What |
|---|---|
| `src/` | The Spring Boot application |
| `frontend-tests/` | Frontend Jest unit tests, Playwright browser e2e tests, and Playwright API tests |
| `api-tests/` | Standalone REST Assured black-box API suite + Pact provider verification |
| `postman/` | Postman collection + Newman CLI runner |
| `contract-tests/` | Pact consumer suite + the generated contract |
| `.github/workflows/` | CI pipeline (see [CI.md](CI.md)) |
| `grafana/`, `prometheus.yml`, `tempo.yml`, `promtail-config.yml` | Observability stack config |

See [TESTING.md](TESTING.md) for how each test directory fits together and how to run them.

## Common commands

```bash
# Run the backend test suite (MockMvc, in-process, H2)
./mvnw test

# Run everything (see TESTING.md for the full sequence, including the black-box suites)
docker compose up -d
./mvnw test
cd frontend-tests && npm ci && npm run test:unit && npx playwright install --with-deps && npm run test:e2e

# Rebuild just the app after a code change, against the running compose stack
docker compose up -d --build app
```

## Known open items

See [TODO.md](TODO.md) for tracked follow-ups (performance testing, UI extraction, etc.).
