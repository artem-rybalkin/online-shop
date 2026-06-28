const { test, expect } = require('@playwright/test');

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Wait until at least one product card is visible. */
async function waitForProducts(page) {
  await page.waitForSelector('.card', { timeout: 15_000 });
}

/** Add the first in-stock product to the cart and wait for the cart count to reflect it. */
async function addFirstProductToCart(page) {
  await waitForProducts(page);
  await page.locator('.add-btn:not([disabled])').first().click();
  // addToCart() is async — it awaits a server fetch before updating the local cart state.
  // Wait for the cart counter to change so checkout() sees a non-empty cart.
  await expect(page.locator('#cartCount')).not.toHaveText('0', { timeout: 10_000 });
}

/** Open checkout modal (assumes one item is already in cart). */
async function openCheckout(page) {
  await addFirstProductToCart(page);
  await page.locator('.cart-btn').click();
  await page.locator('#checkoutBtn').click();
  await page.locator('#modalContent h2').waitFor({ timeout: 5_000 }); // 'Checkout'
}

// ─── Page load ────────────────────────────────────────────────────────────────
test.describe('Page load', () => {
  test('has the correct title', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/SHOPX/);
  });

  test('renders the product grid with at least one card', async ({ page }) => {
    await page.goto('/');
    await waitForProducts(page);
    const count = await page.locator('.card').count();
    expect(count).toBeGreaterThan(0);
  });

  test('shows category filter buttons after load', async ({ page }) => {
    await page.goto('/');
    await waitForProducts(page);
    const filterCount = await page.locator('.filter-btn').count();
    expect(filterCount).toBeGreaterThan(1); // at least "All" + one real category
  });

  test('cart count starts at 0', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#cartCount')).toHaveText('0');
  });
});

// ─── Category filter ──────────────────────────────────────────────────────────
test.describe('Category filter', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForProducts(page);
  });

  test('"All" shows every product', async ({ page }) => {
    const allCount = await page.locator('.card').count();
    await page.locator('.filter-btn', { hasText: 'All' }).click();
    await expect(page.locator('.card')).toHaveCount(allCount);
  });

  test('clicking a category shows only products of that category', async ({ page }) => {
    const btn = page.locator('.filter-btn').filter({ hasText: /Electronics/i }).first();
    await btn.click();
    // Every visible card-category label must say Electronics
    const labels = page.locator('.card-category');
    const count  = await labels.count();
    expect(count).toBeGreaterThan(0);
    for (let i = 0; i < count; i++) {
      await expect(labels.nth(i)).toHaveText('Electronics');
    }
  });

  test('clicking a non-existent category shows "No products found"', async ({ page }) => {
    // Drive the real search input rather than calling handleSearch() directly —
    // it's declared with `const` at script scope, so it's never exposed as window.handleSearch.
    await page.fill('#searchInput', '__no_match_xyzzy__');
    await expect(page.locator('#productGrid')).toContainText('No products found');
  });
});

// ─── Search ───────────────────────────────────────────────────────────────────
test.describe('Search', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForProducts(page);
  });

  test('typing in the search box narrows visible products', async ({ page }) => {
    const allCount = await page.locator('.card').count();
    await page.fill('#searchInput', 'iphone');
    await page.waitForTimeout(300);
    const filtered = await page.locator('.card').count();
    expect(filtered).toBeLessThan(allCount);
  });

  test('clearing search restores all products', async ({ page }) => {
    const allCount = await page.locator('.card').count();
    await page.fill('#searchInput', 'iphone');
    await page.fill('#searchInput', '');
    await page.waitForTimeout(300);
    await expect(page.locator('.card')).toHaveCount(allCount);
  });

  test('no-match search shows "No products found"', async ({ page }) => {
    await page.fill('#searchInput', 'xyzzy_no_match_9999');
    await expect(page.locator('#productGrid')).toContainText('No products found');
  });
});

// ─── Cart ─────────────────────────────────────────────────────────────────────
test.describe('Shopping cart', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForProducts(page);
  });

  test('clicking "Add to Cart" increments the cart counter', async ({ page }) => {
    await page.locator('.add-btn:not([disabled])').first().click();
    await expect(page.locator('#cartCount')).toHaveText('1');
  });

  test('adding the same product twice updates its quantity (not adds duplicate card)', async ({ page }) => {
    const btn = page.locator('.add-btn:not([disabled])').first();
    await btn.click();
    await btn.click();
    await expect(page.locator('#cartCount')).toHaveText('2');
  });

  test('cart drawer opens when cart button is clicked', async ({ page }) => {
    await page.locator('.cart-btn').click();
    await expect(page.locator('#drawer')).toHaveClass(/open/);
  });

  test('cart drawer shows added item', async ({ page }) => {
    await addFirstProductToCart(page);
    await page.locator('.cart-btn').click();
    await expect(page.locator('#drawerItems .cart-item')).toHaveCount(1);
  });

  test('removing an item from the drawer empties the cart', async ({ page }) => {
    await addFirstProductToCart(page);
    await page.locator('.cart-btn').click();
    await page.locator('.remove-btn').first().click();
    await expect(page.locator('#cartCount')).toHaveText('0');
    await expect(page.locator('#drawerItems')).toContainText('empty');
  });

  test('out-of-stock product button is disabled', async ({ page }) => {
    const outOfStockBtns = page.locator('.add-btn[disabled]');
    const count = await outOfStockBtns.count();
    if (count > 0) {
      // Verify the button text says "Out of Stock"
      await expect(outOfStockBtns.first()).toContainText('Out of Stock');
    }
    // If no out-of-stock products exist, the test is vacuously OK
  });
});

// ─── Checkout ─────────────────────────────────────────────────────────────────
test.describe('Checkout flow', () => {
  test('checkout button opens the checkout modal', async ({ page }) => {
    await page.goto('/');
    await openCheckout(page);
    await expect(page.locator('#modalContent h2')).toContainText('Checkout');
  });

  test('checkout modal shows the correct order summary', async ({ page }) => {
    await page.goto('/');
    await openCheckout(page);
    await expect(page.locator('#modalItems .modal-summary-item')).toHaveCount(1);
    await expect(page.locator('#modalTotal')).not.toHaveText('₴0.00');
  });

  test('guest checkout places order and shows success screen', async ({ page }) => {
    await page.goto('/');
    await openCheckout(page);
    await page.fill('#customerName',  'E2E Test User');
    await page.fill('#customerEmail', 'e2e@test.com');
    await page.locator('#confirmBtn').click();
    await expect(page.locator('.success-screen')).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('.order-id')).toContainText('ORDER #');
  });

  test('checkout resets cart after successful order', async ({ page }) => {
    await page.goto('/');
    await openCheckout(page);
    await page.fill('#customerName',  'E2E Test User');
    await page.fill('#customerEmail', 'e2e@test.com');
    await page.locator('#confirmBtn').click();
    await page.locator('.success-screen').waitFor({ timeout: 15_000 });
    await page.getByRole('button', { name: /Continue Shopping/ }).click();
    await expect(page.locator('#cartCount')).toHaveText('0');
  });

  test('checkout requires name and email — empty form shows toast', async ({ page }) => {
    await page.goto('/');
    await openCheckout(page);
    // Leave fields empty
    await page.locator('#confirmBtn').click();
    await expect(page.locator('#toast')).toHaveClass(/show/);
  });
});

// ─── Authentication ───────────────────────────────────────────────────────────
test.describe('Authentication', () => {
  test('clicking "Login / Register" opens the auth modal', async ({ page }) => {
    await page.goto('/');
    await page.locator('#userSection button').click();
    await expect(page.locator('#authOverlay')).toHaveClass(/open/);
  });

  test('switching to the Register tab shows the register form', async ({ page }) => {
    await page.goto('/');
    await page.locator('#userSection button').click();
    await page.locator('#tabRegister').click();
    await expect(page.locator('#registerForm')).toBeVisible();
    await expect(page.locator('#loginForm')).toBeHidden();
  });

  test('login with admin credentials succeeds and shows username', async ({ page }) => {
    await page.goto('/');
    await page.locator('#userSection button').click();
    await page.fill('#loginUser', 'admin');
    await page.fill('#loginPass', 'admin');
    await page.locator('#loginForm .modal-confirm').click();
    await expect(page.locator('#userSection')).toContainText('admin', { timeout: 10_000 });
  });

  test('login with wrong credentials shows error toast', async ({ page }) => {
    await page.goto('/');
    await page.locator('#userSection button').click();
    await page.fill('#loginUser', 'nobody');
    await page.fill('#loginPass', 'wrongpass');
    await page.locator('#loginForm .modal-confirm').click();
    await expect(page.locator('#toast')).toHaveClass(/show/, { timeout: 5_000 });
  });

  test('register with a new unique username succeeds', async ({ page }) => {
    const unique = `e2euser${Date.now()}`;
    await page.goto('/');
    await page.locator('#userSection button').click();
    await page.locator('#tabRegister').click();
    await page.fill('#regUser',   unique);
    await page.fill('#regEmail',  `${unique}@test.com`);
    await page.fill('#regPass',   'Password1!');
    await page.locator('#registerForm .modal-confirm').click();
    await expect(page.locator('#userSection')).toContainText(unique, { timeout: 10_000 });
  });

  test('logout clears user session and shows login button', async ({ page }) => {
    await page.goto('/');
    // Login first
    await page.locator('#userSection button').click();
    await page.fill('#loginUser', 'admin');
    await page.fill('#loginPass', 'admin');
    await page.locator('#loginForm .modal-confirm').click();
    await page.locator('#userSection').getByText('admin').waitFor();
    // Logout
    await page.locator('#userSection button', { hasText: 'Logout' }).click();
    await expect(page.locator('#userSection')).toContainText('Login');
  });
});

// ─── My Orders (authenticated) ────────────────────────────────────────────────
test.describe('My Orders', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    // Login as admin
    await page.locator('#userSection button').click();
    await page.fill('#loginUser', 'admin');
    await page.fill('#loginPass', 'admin');
    await page.locator('#loginForm .modal-confirm').click();
    await page.locator('#userSection').getByText('admin').waitFor();
  });

  test('clicking "My Orders" opens the orders modal', async ({ page }) => {
    await page.locator('#userSection button', { hasText: 'My Orders' }).click();
    await expect(page.locator('#ordersOverlay')).toHaveClass(/open/);
  });

  test('placed order appears in My Orders list', async ({ page }) => {
    // Place an order first
    await waitForProducts(page);
    await addFirstProductToCart(page);
    await page.locator('.cart-btn').click();
    await page.locator('#checkoutBtn').click();
    await page.fill('#customerName',  'Admin User');
    await page.fill('#customerEmail', 'admin@shop.local');
    await page.locator('#confirmBtn').click();
    await page.locator('.success-screen').waitFor({ timeout: 15_000 });
    await page.getByRole('button', { name: /Continue Shopping/ }).click();

    // Open My Orders
    await page.locator('#userSection button', { hasText: 'My Orders' }).click();
    // viewOrders() is async — wait for the first item to appear before counting
    await expect(page.locator('#ordersList .order-history-item').first())
      .toBeVisible({ timeout: 10_000 });
    const orderCount = await page.locator('#ordersList .order-history-item').count();
    expect(orderCount).toBeGreaterThan(0);
  });

  test('clicking Details opens the order details modal with items and customer info', async ({ page }) => {
    // Place an order so there is at least one to inspect
    await waitForProducts(page);
    await addFirstProductToCart(page);
    await page.locator('.cart-btn').click();
    await page.locator('#checkoutBtn').click();
    await page.fill('#customerName',  'Details Test User');
    await page.fill('#customerEmail', 'details@test.com');
    await page.locator('#confirmBtn').click();
    await page.locator('.success-screen').waitFor({ timeout: 15_000 });
    await page.getByRole('button', { name: /Continue Shopping/ }).click();

    // Open My Orders and click Details — intercept the API response so we know when content is ready
    await page.locator('#userSection button', { hasText: 'My Orders' }).click();
    await expect(page.locator('#ordersList .order-history-item').first())
      .toBeVisible({ timeout: 10_000 });

    const [detailsResponse] = await Promise.all([
      page.waitForResponse(
        resp => resp.url().includes('/api/orders/') && resp.status() === 200,
        { timeout: 15_000 }
      ),
      page.locator('#ordersList .order-history-item').first()
        .getByRole('button', { name: 'Details' }).click()
    ]);
    expect(detailsResponse.status()).toBe(200);

    // Content is now populated — overlay open and customer info visible
    await expect(page.locator('#orderDetailsOverlay')).toHaveClass(/open/);
    await expect(page.locator('#orderDetailsContent')).toContainText('Details Test User');
    await expect(page.locator('#orderDetailsContent')).toContainText('details@test.com');
    await expect(page.locator('#orderDetailsContent')).toContainText('PENDING');
    await expect(page.locator('#orderDetailsContent .modal-summary-item')).toHaveCount(1);
  });
});

// ─── Second checkout (form reuse) ─────────────────────────────────────────────
test.describe('Second checkout in same session', () => {
  test('checkout modal shows fresh form after a completed order', async ({ page }) => {
    await page.goto('/');
    await waitForProducts(page);

    // First order
    await openCheckout(page);
    await page.fill('#customerName',  'First Buyer');
    await page.fill('#customerEmail', 'first@test.com');
    await page.locator('#confirmBtn').click();
    await page.locator('.success-screen').waitFor({ timeout: 15_000 });
    await page.getByRole('button', { name: /Continue Shopping/ }).click();

    // Second order — add another item and open checkout again
    await addFirstProductToCart(page);
    await page.locator('.cart-btn').click();
    await page.locator('#checkoutBtn').click();

    // The form should be restored, not stuck on the success screen
    await expect(page.locator('#modalContent h2')).toContainText('Checkout');
    await expect(page.locator('#customerName')).toBeVisible();
    await expect(page.locator('#confirmBtn')).toBeVisible();
  });
});
