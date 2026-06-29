# api-tests

Black-box API test suite using [REST Assured](https://rest-assured.io/). Unlike the
`MockMvc` controller tests in `src/test/java`, these make real HTTP calls against a
running instance — no Spring test context involved.

Also contains `contract/ProviderPactVerificationTest` — verifies the running API satisfies
the consumer contract recorded in `../contract-tests/` (see that directory's README).

## Running

Start the app first (see the main project's docker-compose or run it via `mvnw spring-boot:run`),
then:

```bash
cd api-tests
mvn test -Dapi.baseUrl=http://localhost:8080
```

`api.baseUrl` defaults to `http://localhost:8080` if omitted. `API_BASE_URL` env var also works.

These tests assume the app booted with its default (non-"test") profile, so the seed data
`CommandLineRunner` in `OnlineShopApplication` has run (at least one product, `admin` user exist).
