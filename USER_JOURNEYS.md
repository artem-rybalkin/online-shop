# User Journey Flows

How the API endpoints chain together for each major use case. All examples use curl against a local instance (`http://localhost:8080`). The default seed data ships with an `admin`/`admin` account and a small product catalog (iPhone 14, MacBook Air, etc.) — run `docker compose up --build` to get a fresh environment.

Session IDs below are illustrative UUIDs; generate one per browser/client and keep it stable across calls (typically stored in `localStorage`).

---

## 1. Guest shopping — browse, add to cart, checkout

No account required. Identity is entirely the `sessionId` the client generates once.

### 1.1 Browse products

```
GET /api/products?size=10
```

```json
// response
{
  "content": [
    { "id": 1, "name": "iPhone 14", "price": 35000.0, "stock": 50, "category": "Electronics" },
    { "id": 2, "name": "MacBook Air", "price": 45000.0, "stock": 20, "category": "Electronics" }
  ],
  "totalElements": 9
}
```

Search and filter are also available:

```
GET /api/products/search?name=iphone         # case-insensitive substring
GET /api/products/category/Electronics
```

### 1.2 Add to cart

Pick a `sessionId` (any string, typically a UUID — the client generates this once):

```
POST /api/cart/sess-abc123/add
X-Session-Id: sess-abc123
Content-Type: application/json

{ "productId": 1, "quantity": 2 }
```

```json
// response 200
{ "id": 42, "sessionId": "sess-abc123", "quantity": 2, "product": { "id": 1, "name": "iPhone 14" } }
```

Adding the same product again **increments** quantity rather than creating a duplicate row:

```
POST /api/cart/sess-abc123/add
{ "productId": 1, "quantity": 1 }     // → quantity is now 3
```

```json
// 400 if stock would be exceeded
{ "timestamp": "...", "message": "Insufficient stock for product: iPhone 14" }
```

### 1.3 Review cart

```
GET /api/cart/sess-abc123
X-Session-Id: sess-abc123
```

```json
[
  { "id": 42, "quantity": 3, "product": { "id": 1, "name": "iPhone 14", "price": 35000.0 } }
]
```

### 1.4 Remove one item (optional)

```
DELETE /api/cart/item/42?sessionId=sess-abc123
// or
DELETE /api/cart/item/42
X-Session-Id: sess-abc123
```

```
// 204 No Content
```

```json
// 400 — sessionId not supplied at all
{ "message": "sessionId is required" }

// 403 — sessionId supplied but doesn't own this cart item
{ "message": "Access denied" }
```

### 1.5 Place the order

```
POST /api/orders
Content-Type: application/json

{
  "sessionId":     "sess-abc123",
  "customerName":  "Jane Doe",
  "customerEmail": "jane@example.com"
}
```

What happens behind the scenes:
1. Loads cart items for `sess-abc123`.
2. Acquires a **pessimistic write lock** on each product row and re-checks stock.
3. Decrements stock atomically. Rolls back entirely if any product is short.
4. Snapshots product names and prices into `OrderItem` records (so future catalog changes don't alter this order).
5. Clears the cart.

```json
// response 200
{
  "id": 7,
  "status": "PENDING",
  "totalAmount": 105000.00,
  "customerName": "Jane Doe",
  "customerEmail": "jane@example.com",
  "sessionId": "sess-abc123",
  "items": [
    { "productName": "iPhone 14", "price": 35000.00, "quantity": 3 }
  ]
}
```

```json
// 400 — cart was empty
{ "message": "Cart is empty" }

// 400 — a product ran out between addToCart and checkout (race condition caught)
{ "message": "Not enough stock for product: iPhone 14" }
```

### 1.6 View the order later

```
GET /api/orders/7?sessionId=sess-abc123
// or
GET /api/orders/7
X-Session-Id: sess-abc123
```

```json
// 403 — wrong or missing sessionId
{ "message": "Access denied" }
```

---

## 2. Registered user — register, login, shop, view orders, logout

### 2.1 Register

```
POST /api/auth/register
Content-Type: application/json

{ "username": "jane", "password": "correct-horse-battery-staple", "email": "jane@example.com" }
```

```json
// 200 — sets a jwt httpOnly cookie automatically
{ "username": "jane", "email": "jane@example.com" }

// 400 — validation error
{ "message": "Username is required" }     // blank username
{ "message": "Password is required" }     // blank password
{ "message": "Please provide a valid email address" }

// 400 — duplicate
{ "error": "Username already exists" }
```

### 2.2 Login

The `email` field is **not** sent on login (the frontend never sends it; the server accepts the request without it):

```
POST /api/auth/login
Content-Type: application/json

{ "username": "jane", "password": "correct-horse-battery-staple" }
```

```json
// 200 — same shape as register; jwt cookie is set
{ "username": "jane", "email": "jane@example.com" }

// 401
{ "message": "Invalid username or password" }
```

After a successful login the browser automatically includes the `jwt` cookie on every subsequent request to `localhost:8080`. curl users must pass `-b "jwt=<token>"` or use a cookie jar (`-c cookie.jar / -b cookie.jar`).

### 2.3 Check who is logged in

```
GET /api/auth/me
// (jwt cookie sent automatically)
```

```json
// 200
{ "username": "jane", "email": "jane@example.com" }

// 401 — no valid cookie
```

### 2.4 Shop (same endpoints as guest, now with identity attached)

Cart and order endpoints are still public — authentication is optional. But if a valid JWT cookie is present when `POST /api/orders` is called, the resulting order is linked to the authenticated user account (in addition to the `sessionId`). This matters for order retrieval (see §2.5).

```
POST /api/cart/sess-def456/add
{ "productId": 2, "quantity": 1 }

POST /api/orders
{ "sessionId": "sess-def456", "customerName": "Jane Doe", "customerEmail": "jane@example.com" }
// jwt cookie present → order.user = jane
```

### 2.5 List my orders

```
GET /api/orders/my
X-Session-Id: sess-def456     ← optional when authenticated
```

When **both** a JWT cookie and a sessionId are present, the response is a **union** — orders linked to `jane` OR orders linked to `sess-def456`, whichever came first. This means a guest order placed before login still shows up after the user logs in, provided the same sessionId is still sent.

```json
// 200
{
  "content": [
    { "id": 7, "status": "PENDING", "totalAmount": 45000.00, "customerName": "Jane Doe" }
  ],
  "totalElements": 1
}
```

Ownership check for a specific order (`GET /api/orders/{id}`) uses the same OR logic: `order.user.username == "jane"` OR `order.sessionId == sess-def456`. Either match grants access; neither match → 403.

### 2.6 Logout

```
POST /api/auth/logout
```

```json
// 200 — jwt cookie is expired (Max-Age=0)
{ "message": "Logged out" }
```

---

## 3. Guest → registered user (seamless order history)

A common pattern: a user shops as a guest, then decides to register. Their in-progress guest orders should still be accessible after login.

```
// Step 1 — guest adds to cart and places an order
POST /api/cart/sess-xyz/add    { "productId": 3, "quantity": 1 }
POST /api/orders               { "sessionId": "sess-xyz", "customerName": "Guest", "customerEmail": "g@example.com" }
// → order id 9, order.user = null, order.sessionId = "sess-xyz"

// Step 2 — guest registers and logs in (jwt cookie now set)
POST /api/auth/register        { "username": "newuser", "password": "pw", "email": "u@example.com" }

// Step 3 — GET /api/orders/my with BOTH the jwt cookie AND the old sessionId
GET /api/orders/my
X-Session-Id: sess-xyz     ← still sending the same session

// response includes order 9 (found via sessionId match)
// even though order.user is null, the sessionId union picks it up
```

---

## 4. Admin — product management

The seed `admin`/`admin` account has `ROLE_ADMIN`. Log in to get the JWT cookie, then all `POST /DELETE /api/products` routes are available.

### 4.1 Create a product

```
POST /api/products
Content-Type: application/json
// (admin jwt cookie)

{
  "name":        "Samsung Galaxy S24",
  "description": "Flagship Android",
  "price":       32000.0,
  "stock":       40,
  "category":    "Electronics"
}
```

```json
// 200
{ "id": 10, "name": "Samsung Galaxy S24", "price": 32000.0, "stock": 40, "category": "Electronics" }

// 400 — duplicate name
{ "message": "Product with name 'Samsung Galaxy S24' already exists" }

// 400 — validation
{ "message": "Price must be positive" }    // price ≤ 0
{ "message": "Stock cannot be negative" }  // stock < 0

// 403 — not admin
```

### 4.2 Delete a product

```
DELETE /api/products/10
// (admin jwt cookie)
```

```
// 204 No Content (also returned if the product id doesn't exist — no-op)
```

### 4.3 Reset stock (test/dev utility)

Bulk-sets every product's stock to a given value. Useful after repeated test runs deplete the seed catalog.

```
POST /api/products/reset-stock?stock=9999
// (admin jwt cookie, ?stock defaults to 9999 if omitted)
```

```json
// 200
{ "stock": 9999, "updatedCount": 50 }

// 400 — negative value rejected
{ "message": "Stock cannot be negative" }
```

---

## 5. Error and edge-case flows

### Rate limiting (auth endpoints only)

```
POST /api/auth/login   ×11 within 60 seconds from the same IP
```

```json
// 429 on the 11th+ request (default limit is 10/60s)
{ "timestamp": "...", "message": "Too many requests. Please try again in 60 seconds." }
```

The limit is per-IP. `X-Forwarded-For` is only trusted when the request's direct remote address is in the configured `rate-limit.auth.trusted-proxies` allowlist — an untrusted `X-Forwarded-For` header is ignored.

### JWT cookie expiry

JWT tokens expire after 10 hours. After expiry:

```
GET /api/auth/me    // with expired cookie
// 401 — JwtFilter silently discards the token; SecurityContext stays anonymous
```

The client should redirect to `/api/auth/login` and re-authenticate. The guest cart (by sessionId) is unaffected by JWT expiry.

### Stock race condition

Two concurrent sessions both add the last unit to their carts and race to check out:

```
// Session A → POST /api/orders  { sessionId: "A", ... }   → 200 (gets the stock)
// Session B → POST /api/orders  { sessionId: "B", ... }   → 400 (pessimistic lock detects stock = 0)
{ "message": "Not enough stock for product: MacBook Air" }
```

Session B's order is rolled back entirely — its cart items remain, so the user can retry when stock is replenished.

### Ownership mismatch

```
GET /api/orders/7?sessionId=wrong-session
// 403
{ "message": "Access denied" }

DELETE /api/cart/item/42?sessionId=wrong-session
// 403
{ "message": "You do not have permission to remove this cart item." }
```

### Malformed request

```
POST /api/auth/register
Content-Type: application/json
Body: {broken json

// 400
{ "message": "Malformed or missing request body" }
```

---

## Quick reference — session ID handling

The `sessionId` can be passed two ways; the header is preferred because it doesn't appear in server access logs:

| Method | Example |
|---|---|
| `X-Session-Id` header | `X-Session-Id: sess-abc123` |
| Query param / path variable | `?sessionId=sess-abc123` or `/api/cart/sess-abc123` |

When both are present, the header wins. The two mechanisms are interchangeable and can be mixed across calls — the server cares only about the value, not how it arrived.

---

## See also

- [ARCHITECTURE.md](ARCHITECTURE.md) — data model, security rules, business logic details
- [README.md](README.md) — how to start the app
- Swagger UI (live): `http://localhost:8080/swagger-ui.html`
