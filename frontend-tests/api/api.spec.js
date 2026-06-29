// Pure API tests via Playwright's `request` fixture — no browser, no `page`.
// Each test gets its own isolated APIRequestContext (and cookie jar), unlike
// the e2e/ suite which drives a real browser page.
const { test, expect } = require('@playwright/test');

function uniqueSuffix() {
  return `${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

async function adminCookie(request) {
  const res = await request.post('/api/auth/login', {
    data: { username: 'admin', password: 'admin' }
  });
  expect(res.status()).toBe(200);
}

async function productWithStock(request, minStock) {
  const res = await request.get('/api/products?size=100');
  expect(res.status()).toBe(200);
  const body = await res.json();
  const product = body.content.find(p => p.stock >= minStock);
  if (!product) throw new Error(`No product with at least ${minStock} stock found`);
  return product.id;
}

// ─── Health ───────────────────────────────────────────────────────────────────
test('GET /actuator/health returns UP', async ({ request }) => {
  const res = await request.get('/actuator/health');
  expect(res.status()).toBe(200);
  expect((await res.json()).status).toBe('UP');
});

// ─── Auth ─────────────────────────────────────────────────────────────────────
test.describe('Auth', () => {
  test('register, login, me, logout flow end to end', async ({ request }) => {
    const username = `pw-api-user-${uniqueSuffix()}`;
    const email = `${username}@example.com`;

    const registerRes = await request.post('/api/auth/register', {
      data: { username, password: 'password123', email }
    });
    expect(registerRes.status()).toBe(200);
    expect((await registerRes.json()).username).toBe(username);

    // Login with no email field — matches what the real frontend sends.
    const loginRes = await request.post('/api/auth/login', {
      data: { username, password: 'password123' }
    });
    expect(loginRes.status()).toBe(200);

    const meBefore = await request.get('/api/auth/me');
    expect(meBefore.status()).toBe(200);
    expect((await meBefore.json()).username).toBe(username);

    const logoutRes = await request.post('/api/auth/logout');
    expect(logoutRes.status()).toBe(200);

    const meAfter = await request.get('/api/auth/me');
    expect(meAfter.status()).toBe(401);
  });

  test('login with wrong password returns 401', async ({ request }) => {
    const res = await request.post('/api/auth/login', {
      data: { username: 'nonexistent-pw-api-user', password: 'wrong' }
    });
    expect(res.status()).toBe(401);
    expect((await res.json()).message).toBe('Invalid username or password');
  });

  test('register with blank username returns 400', async ({ request }) => {
    const res = await request.post('/api/auth/register', {
      data: { username: '', password: 'password123', email: `blank-${uniqueSuffix()}@example.com` }
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Username is required');
  });

  test('register with blank password returns 400', async ({ request }) => {
    const res = await request.post('/api/auth/register', {
      data: { username: `pw-blank-${uniqueSuffix()}`, password: '', email: `blank-pw-${uniqueSuffix()}@example.com` }
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Password is required');
  });

  test('register with invalid email returns 400', async ({ request }) => {
    const res = await request.post('/api/auth/register', {
      data: { username: `pw-bademail-${uniqueSuffix()}`, password: 'password123', email: 'not-an-email' }
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Please provide a valid email address');
  });

  test('register with duplicate username returns 400', async ({ request }) => {
    const username = `pw-dup-${uniqueSuffix()}`;
    const first = await request.post('/api/auth/register', {
      data: { username, password: 'password123', email: `${username}@example.com` }
    });
    expect(first.status()).toBe(200);

    const second = await request.post('/api/auth/register', {
      data: { username, password: 'password123', email: `${username}-2@example.com` }
    });
    expect(second.status()).toBe(400);
    expect((await second.json()).error).toBe('Username already exists');
  });

  test('register with malformed JSON returns 400', async ({ request }) => {
    const res = await request.post('/api/auth/register', {
      headers: { 'Content-Type': 'application/json' },
      data: '{not valid json'
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Malformed or missing request body');
  });

  test('GET on the login endpoint returns 405', async ({ request }) => {
    const res = await request.get('/api/auth/login');
    expect(res.status()).toBe(405);
  });
});

// ─── Products ─────────────────────────────────────────────────────────────────
test.describe('Products', () => {
  test('GET /api/products returns a paged list', async ({ request }) => {
    const res = await request.get('/api/products?size=1');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.content.length).toBeGreaterThan(0);
  });

  test('GET /api/products/{id} returns 404 for a missing product', async ({ request }) => {
    const res = await request.get('/api/products/999999');
    expect(res.status()).toBe(404);
  });

  test('POST /api/products without admin JWT returns 403', async ({ request }) => {
    const res = await request.post('/api/products', {
      data: { name: 'pw-api-unauthorized', description: 'x', price: 1.0, stock: 1, category: 'Test' }
    });
    expect(res.status()).toBe(403);
  });

  test('DELETE /api/products/{id} without admin JWT returns 403', async ({ request }) => {
    const productId = await productWithStock(request, 0);
    const res = await request.delete(`/api/products/${productId}`);
    expect(res.status()).toBe(403);
  });

  test('create then delete a product as admin', async ({ request }) => {
    await adminCookie(request);
    const name = `pw-api-admin-product-${uniqueSuffix()}`;

    const createRes = await request.post('/api/products', {
      data: { name, description: 'created by playwright api tests', price: 9.99, stock: 3, category: 'Test' }
    });
    expect(createRes.status()).toBe(200);
    const id = (await createRes.json()).id;

    const deleteRes = await request.delete(`/api/products/${id}`);
    expect(deleteRes.status()).toBe(204);

    const getRes = await request.get(`/api/products/${id}`);
    expect(getRes.status()).toBe(404);
  });

  test('create product with negative price returns 400', async ({ request }) => {
    await adminCookie(request);
    const res = await request.post('/api/products', {
      data: { name: `pw-api-neg-price-${uniqueSuffix()}`, description: 'x', price: -5.0, stock: 1, category: 'Test' }
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Price must be positive');
  });

  test('create product with negative stock returns 400', async ({ request }) => {
    await adminCookie(request);
    const res = await request.post('/api/products', {
      data: { name: `pw-api-neg-stock-${uniqueSuffix()}`, description: 'x', price: 5.0, stock: -1, category: 'Test' }
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Stock cannot be negative');
  });

  test('create product with duplicate name returns 400', async ({ request }) => {
    await adminCookie(request);
    const name = `pw-api-dup-product-${uniqueSuffix()}`;
    const first = await request.post('/api/products', {
      data: { name, description: 'first', price: 5.0, stock: 1, category: 'Test' }
    });
    expect(first.status()).toBe(200);

    const second = await request.post('/api/products', {
      data: { name, description: 'second', price: 6.0, stock: 1, category: 'Test' }
    });
    expect(second.status()).toBe(400);
  });

  test('POST /api/products/reset-stock requires admin and resets every product', async ({ request }) => {
    const unauthorized = await request.post('/api/products/reset-stock');
    expect(unauthorized.status()).toBe(403);

    await adminCookie(request);
    const res = await request.post('/api/products/reset-stock');
    expect(res.status()).toBe(200);
    expect((await res.json()).stock).toBe(9999);
  });

  test('POST /api/products/reset-stock with a negative stock returns 400', async ({ request }) => {
    await adminCookie(request);
    const res = await request.post('/api/products/reset-stock?stock=-1');
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Stock cannot be negative');
  });
});

// ─── Cart ─────────────────────────────────────────────────────────────────────
test.describe('Cart', () => {
  test('add to cart then get cart returns the item', async ({ request }) => {
    const sessionId = `pw-api-cart-${uniqueSuffix()}`;
    const productId = await productWithStock(request, 5);

    const addRes = await request.post(`/api/cart/${sessionId}/add`, {
      data: { productId, quantity: 1 }
    });
    expect(addRes.status()).toBe(200);

    const cartRes = await request.get(`/api/cart/${sessionId}`);
    expect(cartRes.status()).toBe(200);
    const cart = await cartRes.json();
    expect(cart).toHaveLength(1);
    expect(cart[0].product.id).toBe(productId);
  });

  test('add to cart with quantity 0 returns 400', async ({ request }) => {
    const productId = await productWithStock(request, 0);
    const res = await request.post(`/api/cart/pw-api-zero-qty-${uniqueSuffix()}/add`, {
      data: { productId, quantity: 0 }
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Quantity must be at least 1');
  });

  test('add to cart with missing productId returns 400', async ({ request }) => {
    const res = await request.post(`/api/cart/pw-api-no-product-id-${uniqueSuffix()}/add`, {
      data: { quantity: 1 }
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Product ID is required');
  });

  test('add to cart for a missing product returns 404', async ({ request }) => {
    const res = await request.post(`/api/cart/pw-api-no-product-${uniqueSuffix()}/add`, {
      data: { productId: 999999, quantity: 1 }
    });
    expect(res.status()).toBe(404);
  });

  test('add to cart with quantity exceeding stock returns 400', async ({ request }) => {
    const productId = await productWithStock(request, 0);
    const getRes = await request.get(`/api/products/${productId}`);
    const stock = (await getRes.json()).stock;

    const res = await request.post(`/api/cart/pw-api-overstock-${uniqueSuffix()}/add`, {
      data: { productId, quantity: stock + 1 }
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toContain('Insufficient stock');
  });

  test('remove cart item without sessionId returns 400', async ({ request }) => {
    const productId = await productWithStock(request, 5);
    const sessionId = `pw-api-cart-no-session-${uniqueSuffix()}`;

    const addRes = await request.post(`/api/cart/${sessionId}/add`, { data: { productId, quantity: 1 } });
    const cartItemId = (await addRes.json()).id;

    const res = await request.delete(`/api/cart/item/${cartItemId}`);
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('sessionId is required');
  });

  test('remove a non-existent cart item returns 404', async ({ request }) => {
    const res = await request.delete('/api/cart/item/999999?sessionId=any-session');
    expect(res.status()).toBe(404);
  });

  test('remove cart item with the wrong sessionId returns 403', async ({ request }) => {
    const productId = await productWithStock(request, 5);
    const ownerSession = `pw-api-cart-owner-${uniqueSuffix()}`;

    const addRes = await request.post(`/api/cart/${ownerSession}/add`, { data: { productId, quantity: 1 } });
    const cartItemId = (await addRes.json()).id;

    const res = await request.delete(`/api/cart/item/${cartItemId}?sessionId=someone-elses-session`);
    expect(res.status()).toBe(403);
  });
});

// ─── Orders ───────────────────────────────────────────────────────────────────
test.describe('Orders', () => {
  test('place an order then fetch it by id', async ({ request }) => {
    const sessionId = `pw-api-order-${uniqueSuffix()}`;
    const productId = await productWithStock(request, 5);

    await request.post(`/api/cart/${sessionId}/add`, { data: { productId, quantity: 1 } });

    const orderRes = await request.post('/api/orders', {
      data: { sessionId, customerName: 'Playwright API Buyer', customerEmail: 'pwapi@example.com' }
    });
    expect(orderRes.status()).toBe(200);
    const order = await orderRes.json();
    expect(order.items).toHaveLength(1);

    const getRes = await request.get(`/api/orders/${order.id}?sessionId=${sessionId}`);
    expect(getRes.status()).toBe(200);
    expect((await getRes.json()).id).toBe(order.id);

    // Cart should be cleared after a successful order.
    const cartRes = await request.get(`/api/cart/${sessionId}`);
    expect(await cartRes.json()).toHaveLength(0);
  });

  test('placing an order with an empty cart returns 400', async ({ request }) => {
    const res = await request.post('/api/orders', {
      data: { sessionId: `pw-api-empty-${uniqueSuffix()}`, customerName: 'No Items', customerEmail: 'empty@example.com' }
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Cart is empty');
  });

  test('getting an order with the wrong sessionId returns 403', async ({ request }) => {
    const sessionId = `pw-api-order-ownership-${uniqueSuffix()}`;
    const productId = await productWithStock(request, 5);

    await request.post(`/api/cart/${sessionId}/add`, { data: { productId, quantity: 1 } });
    const orderRes = await request.post('/api/orders', {
      data: { sessionId, customerName: 'Owner', customerEmail: 'owner@example.com' }
    });
    const orderId = (await orderRes.json()).id;

    const res = await request.get(`/api/orders/${orderId}?sessionId=wrong-session`);
    expect(res.status()).toBe(403);
  });

  test('getting a non-existent order returns 404', async ({ request }) => {
    const res = await request.get('/api/orders/999999?sessionId=any-session');
    expect(res.status()).toBe(404);
  });

  test('placing an order with a blank customer name returns 400', async ({ request }) => {
    const sessionId = `pw-api-blank-name-${uniqueSuffix()}`;
    const productId = await productWithStock(request, 5);
    await request.post(`/api/cart/${sessionId}/add`, { data: { productId, quantity: 1 } });

    const res = await request.post('/api/orders', {
      data: { sessionId, customerEmail: 'blank-name@example.com' }
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Customer name is required');
  });

  test('placing an order with an invalid customer email returns 400', async ({ request }) => {
    const sessionId = `pw-api-bad-email-${uniqueSuffix()}`;
    const productId = await productWithStock(request, 5);
    await request.post(`/api/cart/${sessionId}/add`, { data: { productId, quantity: 1 } });

    const res = await request.post('/api/orders', {
      data: { sessionId, customerName: 'Bad Email Buyer', customerEmail: 'not-an-email' }
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Must be a valid email address');
  });

  test('placing an order with malformed JSON returns 400', async ({ request }) => {
    const res = await request.post('/api/orders', {
      headers: { 'Content-Type': 'application/json' },
      data: '{not valid json'
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toBe('Malformed or missing request body');
  });

  test('stock drops between addToCart and checkout is caught by the order-time recheck', async ({ request }) => {
    // Self-contained admin-created product, isolated from shared seed-data stock.
    await adminCookie(request);
    const productName = `pw-api-stock-recheck-${uniqueSuffix()}`;
    const createRes = await request.post('/api/products', {
      data: { name: productName, description: 'x', price: 10.0, stock: 2, category: 'Test' }
    });
    const productId = (await createRes.json()).id;
    await request.post('/api/auth/logout');

    const firstSession = `pw-api-stock-recheck-first-${uniqueSuffix()}`;
    const secondSession = `pw-api-stock-recheck-second-${uniqueSuffix()}`;

    for (const sessionId of [firstSession, secondSession]) {
      const res = await request.post(`/api/cart/${sessionId}/add`, { data: { productId, quantity: 2 } });
      expect(res.status()).toBe(200);
    }

    const firstOrder = await request.post('/api/orders', {
      data: { sessionId: firstSession, customerName: 'First Buyer', customerEmail: `first-${productId}@example.com` }
    });
    expect(firstOrder.status()).toBe(200);

    const secondOrder = await request.post('/api/orders', {
      data: { sessionId: secondSession, customerName: 'Second Buyer', customerEmail: `second-${productId}@example.com` }
    });
    expect(secondOrder.status()).toBe(400);
    expect((await secondOrder.json()).message).toBe(`Not enough stock for product: ${productName}`);
  });
});
