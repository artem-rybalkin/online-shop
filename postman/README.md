# postman

Postman collection for manual/exploratory API testing, plus a [Newman](https://github.com/postmanlabs/newman)
runner for CLI/CI use.

## Running with Newman

```bash
cd postman
npm install
npm run test:api
```

Defaults to `http://localhost:8080`. Override the environment's `baseUrl` for other targets.

## Running in the Postman app

Import `online-shop.postman_collection.json` and `online-shop.postman_environment.json`, select the
"Local" environment, then run the collection (folders execute top-to-bottom: Health → Setup →
Auth → Products → Cart → Orders). The "Setup" folder logs in as the seeded `admin` user and resets
every product's stock to 9999 via `POST /api/products/reset-stock`, so the run doesn't depend on
however much stock previous runs have consumed.
