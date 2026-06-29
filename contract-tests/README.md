# contract-tests

Consumer-driven contract testing with [Pact](https://docs.pact.io/).

- `consumer/` — a small pact-js suite describing what the frontend expects from the API
  (login, products, cart, orders). Running it generates/updates the contract file in `pacts/`.
- `pacts/` — the generated contract (`OnlineShopFrontend-OnlineShopAPI.json`), committed so the
  provider side can verify against it without needing the consumer suite to run first.
- The provider verification lives in `../api-tests/src/test/java/com/shop/apitests/contract/`
  (next to the other "needs a real running server" tests) — see that module's README for how
  to run it.

## Regenerating the contract

Whenever the frontend's expectations of the API change (new endpoint, new field, etc.):

```bash
cd contract-tests/consumer
npm install
npm run test:pact
```

This overwrites `pacts/OnlineShopFrontend-OnlineShopAPI.json`. Commit the updated file, then
re-run the provider verification in `api-tests` to confirm the real API still satisfies it.

## Known limitation

The "order 1 exists and belongs to a different session" provider state assumes order id `1`
already exists in the target database (true for this repo's dev database, accumulated from
normal use/testing). A genuinely fresh database needs one order placed before that interaction
can be verified — the state handler doesn't create it itself, since the pact interaction's
request path hardcodes `/api/orders/1`.
