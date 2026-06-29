# CI Pipeline

`.github/workflows/ci.yml` — runs on every push and pull request to `main`. Two jobs, sequential.

## Job 1: `backend-tests`

```yaml
./mvnw -B test
```

The fast in-process suite (see [TESTING.md](TESTING.md) suite 1) — H2 in-memory, no Docker, no
real server. This is the gate: `e2e-tests` declares `needs: backend-tests` and won't start until
this passes.

## Job 2: `e2e-tests`

Everything that needs a real running server: a MySQL **service container**, the actual built
jar, and then every black-box suite from [TESTING.md](TESTING.md) (suites 3–7) against it.

| Step | What | Why |
|---|---|---|
| `mysql` service container | `mysql:8.0`, health-checked | A real database — the whole point of this job vs. job 1's H2 |
| Build app jar | `./mvnw -B -DskipTests package` | Tests already ran in job 1; don't re-run them here |
| Start app | `nohup java -jar target/*.jar &`, poll `/actuator/health` for up to 120s | Backgrounds the app for the rest of the job; fails the job if it never comes up, dumping `app.log` |
| Run frontend unit tests | `npm run test:unit` | Suite 2 — doesn't need the server, but runs here for convenience alongside the rest of the Node tooling |
| Install Playwright browsers | `npx playwright install --with-deps chromium firefox` | Needed before suites 3–4 |
| Run e2e tests | `npm run test:e2e` | Suites 3 *and* 4 — `playwright.config.js` defines three projects (chromium, firefox, api) and this one command runs all of them |
| Generate consumer contract | `cd contract-tests/consumer && npm ci && npm run test:pact` | Regenerates the Pact contract fresh every run rather than trusting a possibly-stale committed file |
| Run api-tests | `cd api-tests && mvn test -Dapi.baseUrl=http://localhost:8080` | Suite 5, plus the Pact provider verification (suite 7) — same Maven module, same `mvn test` |
| Run Postman collection | `cd postman && npm ci && npm run test:api` | Suite 6 |

### Env vars set for this job

| Var | Value | Why |
|---|---|---|
| `DB_HOST` | `127.0.0.1` | The app and the MySQL service container share the runner's network namespace |
| `DB_PASSWORD`, `JWT_SECRET` | Fixed CI-only values | Required — the app has no fallback for either and refuses to start without them |
| `COOKIE_SECURE` | `false` | CI runs over plain HTTP |
| `RATE_LIMIT_AUTH_MAX` | `1000` | **Important**: the default (10 req/60s) exists to throttle real abuse, but every black-box suite logs in/registers far more than 10 times/minute from one IP. Without this override, the suites trip the rate limiter and fail with 429s that have nothing to do with what they're testing. This is a CI-only override — production should keep the conservative default. |

### Debugging a failed run

- **Playwright report** — uploaded as an artifact (`playwright-report`) only `if: failure()`.
  Download it from the failed run's Actions page for screenshots/videos/traces of the exact
  failure.
- **App logs** — `cat app.log` runs `if: failure()` at the end of the job, dumping everything the
  app printed during the run (including the "did not become healthy" case, if it never started).
- **Reproducing locally** — see [TESTING.md](TESTING.md)'s "Running everything locally" section.
  The only structural difference from CI is using `docker compose`'s persistent MySQL instead of
  an ephemeral service container, and not needing the `RATE_LIMIT_AUTH_MAX` override if your local
  `.env` already raises it (check before assuming a local pass means CI will too).
