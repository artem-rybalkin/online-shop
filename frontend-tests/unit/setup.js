/**
 * Runs after Jest's test framework is installed (jest.fn() etc. are available).
 * Bootstraps jsdom with the minimal DOM from index.html and loads app.js so
 * all global functions (getImage, renderProducts, loadProducts …) are on window.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '../../src/main/resources/static');

// ─── localStorage / sessionStorage mocks ─────────────────────────────────────
function makeStorage() {
  const store = {};
  return {
    getItem:    (k) => (k in store ? store[k] : null),
    setItem:    (k, v) => { store[k] = String(v); },
    removeItem: (k) => { delete store[k]; },
    clear:      () => { Object.keys(store).forEach(k => delete store[k]); }
  };
}
Object.defineProperty(window, 'localStorage',  { value: makeStorage(), configurable: true });
Object.defineProperty(window, 'sessionStorage', { value: makeStorage(), configurable: true });

// ─── fetch mock (default: empty Page response) ────────────────────────────────
global.fetch = jest.fn(() =>
  Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve({ content: [], totalElements: 0 })
  })
);

// ─── Minimal DOM (mirrors index.html structure) ───────────────────────────────
document.body.innerHTML = `
  <header>
    <div id="userSection">
      <button class="auth-trigger" onclick="toggleAuthModal()">Login / Register</button>
    </div>
    <button class="cart-btn" onclick="toggleCart()">
      Cart <span id="cartCount">0</span>
    </button>
  </header>
  <div id="searchInput"></div>
  <div id="categoryFilters"></div>
  <div id="productGrid"><div class="status">Loading...</div></div>
  <div id="overlay"></div>
  <div id="drawer">
    <div id="drawerItems"><div class="drawer-empty">Your cart is empty</div></div>
    <span id="totalVal">0.00</span>
    <button id="checkoutBtn" onclick="checkout()">Checkout</button>
  </div>
  <div id="authOverlay">
    <div id="loginForm">
      <input id="loginUser" /><input id="loginPass" />
      <button onclick="handleAuth('login')">Sign In</button>
    </div>
    <div id="registerForm" style="display:none">
      <input id="regUser" /><input id="regEmail" /><input id="regPass" />
      <button onclick="handleAuth('register')">Create Account</button>
    </div>
    <button id="tabLogin"    onclick="switchAuthTab('login')">Login</button>
    <button id="tabRegister" onclick="switchAuthTab('register')">Register</button>
  </div>
  <div id="modalOverlay">
    <div id="modalContent">
      <div id="modalItems"></div>
      <span id="modalTotal"></span>
      <input id="customerName" /><input id="customerEmail" />
      <button id="confirmBtn" onclick="placeOrder()">Place Order</button>
    </div>
  </div>
  <div id="ordersOverlay"><div id="ordersList"></div></div>
  <div id="orderDetailsOverlay">
    <h2 id="detailsTitle"></h2>
    <div id="orderDetailsContent"></div>
  </div>
  <div id="toast"></div>
`;

// ─── Execute app.js inside jsdom (runScripts:'dangerously' enables this) ──────
const scriptEl = document.createElement('script');
scriptEl.textContent = fs.readFileSync(path.join(ROOT, 'js/app.js'), 'utf8');
document.head.appendChild(scriptEl);
// After this point every function defined with `function` in app.js
// is a property of `window` and directly callable in tests.
