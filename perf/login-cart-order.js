/**
 * k6 perf test — login → browse → add to cart → place order
 *
 * Prerequisites:
 *   1. App running:  docker compose up -d
 *   2. Reset stock:  docker compose exec db mysql -u root -p<MYSQL_ROOT_PASSWORD> shop_db \
 *                      -e "UPDATE products SET stock = 99999;"
 *   3. Run:          k6 run perf/login-cart-order.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { Trend, Rate } from 'k6/metrics';

// ── Custom metrics ────────────────────────────────────────────────────────────
const loginDuration    = new Trend('login_duration',    true);
const cartDuration     = new Trend('cart_duration',     true);
const orderDuration    = new Trend('order_duration',    true);
const orderFailureRate = new Rate('order_failure_rate');

// ── Test config ───────────────────────────────────────────────────────────────
const BASE = 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '20s', target: 5  },  // ramp up
    { duration: '1m',  target: 10 },  // steady state
    { duration: '10s', target: 0  },  // ramp down
  ],
  thresholds: {
    http_req_failed:   ['rate<0.05'],   // < 5% HTTP errors overall
    http_req_duration: ['p(95)<2000'],  // 95th percentile under 2 s
    login_duration:    ['p(95)<1000'],  // login under 1 s
    order_duration:    ['p(95)<3000'],  // order under 3 s
    order_failure_rate: ['rate<0.05'],  // < 5% order failures
  },
};

// ── Helpers ───────────────────────────────────────────────────────────────────
function jsonHeaders(token, sessionId) {
  const h = { 'Content-Type': 'application/json', 'X-Session-Id': sessionId };
  if (token) h['Authorization'] = `Bearer ${token}`;
  return h;
}

// ── Main flow ─────────────────────────────────────────────────────────────────
export default function () {
  const sessionId = uuidv4();
  let token = null;

  // ── 1. Login ─────────────────────────────────────────────────────────────
  group('login', () => {
    const res = http.post(
      `${BASE}/api/auth/login`,
      JSON.stringify({ username: 'admin', password: 'admin' }),
      { headers: { 'Content-Type': 'application/json' },
        tags: { name: 'POST /api/auth/login' } }
    );
    loginDuration.add(res.timings.duration);

    const ok = check(res, {
      'login: status 200':   r => r.status === 200,
      'login: token present': r => { try { return !!JSON.parse(r.body).token; } catch { return false; } },
    });

    if (!ok) return;
    token = JSON.parse(res.body).token;
  });

  if (!token) return;

  sleep(0.5);

  // ── 2. Browse products ────────────────────────────────────────────────────
  let product = null;

  group('browse', () => {
    const res = http.get(
      `${BASE}/api/products?size=20`,
      { headers: jsonHeaders(token, sessionId),
        tags: { name: 'GET /api/products' } }
    );

    check(res, { 'browse: status 200': r => r.status === 200 });

    const body = JSON.parse(res.body);
    const all  = body.content ?? body;
    const available = all.filter(p => p.stock > 0);

    if (available.length) {
      product = available[Math.floor(Math.random() * available.length)];
    }
  });

  if (!product) return;

  sleep(0.5);

  // ── 3. Add to cart ────────────────────────────────────────────────────────
  let cartOk = false;

  group('add_to_cart', () => {
    const res = http.post(
      `${BASE}/api/cart/${sessionId}/add`,
      JSON.stringify({ productId: product.id, quantity: 1 }),
      { headers: jsonHeaders(token, sessionId),
        tags: { name: 'POST /api/cart/add' } }
    );
    cartDuration.add(res.timings.duration);

    cartOk = check(res, { 'cart: status 200': r => r.status === 200 });
  });

  if (!cartOk) return;

  sleep(0.5);

  // ── 4. Place order ────────────────────────────────────────────────────────
  group('place_order', () => {
    const res = http.post(
      `${BASE}/api/orders`,
      JSON.stringify({
        sessionId,
        customerName:  `Perf VU${__VU}`,
        customerEmail: `vu${__VU}@perf.test`,
      }),
      { headers: jsonHeaders(token, sessionId),
        tags: { name: 'POST /api/orders' } }
    );
    orderDuration.add(res.timings.duration);

    const ok = check(res, {
      'order: status 200': r => r.status === 200,
      'order: id present': r => { try { return !!JSON.parse(r.body).id; } catch { return false; } },
    });

    // 400 = out of stock → expected under heavy load, not counted as HTTP failure
    orderFailureRate.add(!ok && res.status !== 400);
  });

  sleep(1);
}
