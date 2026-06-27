const API = '/api';

function escapeHtml(str) {
  const d = document.createElement('div');
  d.textContent = str ?? '';
  return d.innerHTML;
}
let allProducts = [];
var cart = {};
var currentCategory = 'All';
var searchQuery = '';
let checkoutFormTemplate = '';

// Унікальний session ID для цього користувача
let SESSION_ID = localStorage.getItem('cartSessionId');
if (!SESSION_ID) {
  SESSION_ID = 'session_' + Math.random().toString(36).substr(2, 9);
  localStorage.setItem('cartSessionId', SESSION_ID);
}

let currentUser = null;
let currentEmail = null;

function getAuthHeaders() {
  return { 'Content-Type': 'application/json', 'X-Session-Id': SESSION_ID };
}

// The JWT lives in an httpOnly cookie the browser manages — ask the server who's logged in.
async function checkAuth() {
  try {
    const res = await fetch(`${API}/auth/me`, { credentials: 'include' });
    if (res.ok) {
      const data = await res.json();
      currentUser = data.username;
      currentEmail = data.email;
    } else {
      currentUser = null;
      currentEmail = null;
    }
  } catch (e) {
    currentUser = null;
    currentEmail = null;
  }
  updateAuthUI();
}

// ── PRODUCT IMAGES (Unsplash) ──
const productImages = {
  'iphone 14':      'https://images.unsplash.com/photo-1663499482523-1c0c1bae4ce1?w=600&q=80',
  'samsung galaxy': 'https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?w=600&q=80',
  'macbook':        'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&q=80',
  'nike':           'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&q=80',
  'adidas':         'https://images.unsplash.com/photo-1608231387042-66d1773070a5?w=600&q=80',
  'levis':          'https://images.unsplash.com/photo-1542272454315-4c01d7abdf4a?w=600&q=80',
  'sony':           'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&q=80',
  'lg':             'https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?w=600&q=80',
  'xiaomi':         'https://images.unsplash.com/photo-1575311373937-040b8e1fd5b6?w=600&q=80',
  'puma':           'https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?w=600&q=80',
  'coffee':         'https://images.unsplash.com/photo-1517668808822-9ebb02f2a0e6?w=600&q=80',
  'lamp':           'https://images.unsplash.com/photo-1507473885765-e6ed657f997a?w=600&q=80',
};

function getImage(name) {
  if (!name) return 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&q=80';
  const lowerName = name.toLowerCase();
  const key = Object.keys(productImages).find(k => lowerName.includes(k));
  return key
    ? productImages[key]
    : 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&q=80';
}

function getCookie(name) {
  const value = `; ${document.cookie}`;
  const parts = value.split(`; ${name}=`);
  if (parts.length === 2) return parts.pop().split(';').shift();
}

// ── LOAD PRODUCTS ──
async function loadProducts() {
  try {
    const res = await fetch(`${API}/products?size=500`);
    if (!res.ok) throw new Error(`HTTP Error ${res.status}`);
    const data = await res.json();
    allProducts = Array.isArray(data) ? data : (data.content ?? []);
    renderCategories();
    applyFilters();
  } catch (e) {
    console.error('Connection error:', e);
    document.getElementById('productGrid').innerHTML =
      `<div class="status">⚠️ ${e.message || 'Could not connect to API.'}<br>
      Make sure Spring Boot is running and access is permitted.</div>`;
  }
}

// ── RENDER CATEGORIES DYNAMICALLY ──
function renderCategories() {
  const container = document.getElementById('categoryFilters');
  if (!container) return;

  // Extract unique categories from allProducts
  const categories = ['All', ...new Set(allProducts.map(p => p.category))];
  
  container.innerHTML = categories.map(cat => {
    const count = cat === 'All' 
      ? allProducts.length 
      : allProducts.filter(p => p.category === cat).length;

    return `
      <button 
        class="filter-btn ${cat === currentCategory ? 'active' : ''}" 
        onclick="filterBy('${cat}', this)">
        ${cat} <small>(${count})</small>
      </button>
    `;
  }).join('');
}

// ── RENDER PRODUCTS ──
function renderProducts(products) {
  const grid = document.getElementById('productGrid');
  if (!products.length) {
    grid.innerHTML = '<div class="status">No products found.</div>';
    return;
  }
  grid.innerHTML = products.map((p, i) => {
    const isOutOfStock = p.stock <= 0;
    return `
    <div class="card" style="animation-delay:${i * 0.04}s">
      <div class="card-img-wrap">
        <img class="card-img" src="${getImage(p.name)}" alt="${escapeHtml(p.name)}" loading="lazy"/>
      </div>
      <div class="card-category">${escapeHtml(p.category)}</div>
      <div class="card-name">${escapeHtml(p.name)}</div>
      <div class="card-desc">${escapeHtml(p.description)}</div>
      <div class="card-footer">
        <div>
          <div class="card-price">₴${p.price.toLocaleString()}</div>
          <div class="card-stock ${p.stock < 20 ? 'low' : ''}">${p.stock} in stock</div>
        </div>
      </div>
      <button class="add-btn ${cart[p.id] ? 'added' : ''}"
        id="btn-${p.id}"
        data-id="${p.id}" data-name="${escapeHtml(p.name)}" data-price="${p.price}"
        onclick="addToCart(+this.dataset.id, this.dataset.name, +this.dataset.price)"
        ${isOutOfStock ? 'disabled' : ''}>
        ${isOutOfStock ? 'Out of Stock' : (cart[p.id] ? '✓ Added' : '+ Add to Cart')}
      </button>
    </div>
  `; }).join('');
}

// ── FILTERS ──
function filterBy(category, el) {
  document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
  el.classList.add('active');
  currentCategory = category;
  applyFilters();
}

const handleSearch = (function () {
  let timer;
  return function (val) {
    clearTimeout(timer);
    timer = setTimeout(() => {
      searchQuery = val.toLowerCase();
      applyFilters();
    }, 300);
  };
})();

function applyFilters() {
  let filtered = allProducts;
  if (currentCategory !== 'All') {
    filtered = filtered.filter(p => p.category === currentCategory);
  }
  if (searchQuery) {
    filtered = filtered.filter(p =>
      p.name.toLowerCase().includes(searchQuery) ||
      p.description.toLowerCase().includes(searchQuery)
    );
  }
  renderProducts(filtered);
}

// ── CART — ADD ──
async function addToCart(productId, name, price) {
  const product = allProducts.find(p => p.id === productId);
  const currentQty = cart[productId] ? cart[productId].qty : 0;
  if (product && currentQty + 1 > product.stock) {
    showToast(`Sorry, only ${product.stock} items available in stock!`);
    return;
  }

  try {
    await fetch(`${API}/cart/${SESSION_ID}/add`, {
      method: 'POST',
      headers: getAuthHeaders(),
      credentials: 'include',
      body: JSON.stringify({ productId, quantity: 1 })
    });
  } catch (e) {
    // офлайн-режим — продовжуємо локально
  }

  if (cart[productId]) {
    cart[productId].qty++;
  } else {
    cart[productId] = { id: productId, name, price, qty: 1 };
  }

  updateCartUI();
  showToast(`${name} added to cart!`);

  const btn = document.getElementById(`btn-${productId}`);
  if (btn) { btn.textContent = '✓ Added'; btn.classList.add('added'); }
}

// ── CART — REMOVE ──
async function removeFromCart(productId) {
  try {
    const res = await fetch(`${API}/cart/${SESSION_ID}`, { credentials: 'include' });
    const items = await res.json();
    const item = items.find(i => i.product.id === productId);
    if (item) {
      await fetch(`${API}/cart/item/${item.id}`, {
        method: 'DELETE',
        headers: getAuthHeaders(),
        credentials: 'include'
      });
    }
  } catch (e) {}

  delete cart[productId];
  updateCartUI();
  applyFilters();
}

// ── CART — CHANGE QTY ──
function changeQty(productId, delta) {
  if (!cart[productId]) return;
  if (delta > 0) {
    const product = allProducts.find(p => p.id === productId);
    if (product && cart[productId].qty + delta > product.stock) {
      showToast(`Only ${product.stock} items available in stock!`);
      return;
    }
  }
  cart[productId].qty += delta;
  if (cart[productId].qty <= 0) removeFromCart(productId);
  else updateCartUI();
}

// ── UPDATE CART UI ──
function updateCartUI() {
  const items = Object.values(cart);
  const count = items.reduce((s, i) => s + i.qty, 0);
  const total = items.reduce((s, i) => s + i.price * i.qty, 0);

  document.getElementById('cartCount').textContent = count;
  document.getElementById('totalVal').textContent =
    `₴${total.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  const el = document.getElementById('drawerItems');
  if (!items.length) {
    el.innerHTML = '<div class="drawer-empty">Your cart is empty</div>';
    return;
  }

  el.innerHTML = items.map(item => `
    <div class="cart-item">
      <div class="cart-item-info">
        <div class="cart-item-name">${escapeHtml(item.name)}</div>
        <div class="cart-item-price">₴${(item.price * item.qty).toLocaleString()}</div>
        <div class="cart-item-qty">
          <button class="qty-btn" onclick="changeQty(${item.id}, -1)">−</button>
          <span class="qty-val">${item.qty}</span>
          <button class="qty-btn" onclick="changeQty(${item.id}, 1)">+</button>
        </div>
      </div>
      <button class="remove-btn" onclick="removeFromCart(${item.id})">✕</button>
    </div>
  `).join('');
}

// ── CART DRAWER ──
function toggleCart() {
  document.getElementById('drawer').classList.toggle('open');
  document.getElementById('overlay').classList.toggle('open');
}

// ── CHECKOUT MODAL ──
function checkout() {
  if (!Object.keys(cart).length) { showToast('Your cart is empty!'); return; }
  toggleCart();

  // Відновлюємо форму оформлення, якщо вона була замінена повідомленням про успіх
  if (checkoutFormTemplate) {
    document.getElementById('modalContent').innerHTML = checkoutFormTemplate;
  }

  const items = Object.values(cart);
  const total = items.reduce((s, i) => s + i.price * i.qty, 0);

  document.getElementById('modalItems').innerHTML = items.map(item => `
    <div class="modal-summary-item">
      <span>${escapeHtml(item.name)} × ${item.qty}</span>
      <span>₴${(item.price * item.qty).toLocaleString()}</span>
    </div>
  `).join('');

  document.getElementById('modalTotal').textContent =
    `₴${total.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  document.getElementById('customerName').value = currentUser || '';
  document.getElementById('customerEmail').value = currentEmail || '';

  showModal();
}

function showModal() {
  document.getElementById('modalOverlay').classList.add('open');
}

function closeModal() {
  document.getElementById('modalOverlay').classList.remove('open');
}

// ── AUTH LOGIC ──
function toggleAuthModal() {
  const modal = document.getElementById('authOverlay');
  const isClosing = modal.classList.contains('open');
  modal.classList.toggle('open');
  
  // Якщо ми закриваємо модалку, очищуємо поля
  if (isClosing) clearAuthFields();
}

function clearAuthFields() {
  const fields = ['loginUser', 'loginPass', 'regUser', 'regPass', 'regEmail'];
  fields.forEach(id => {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });
}

function switchAuthTab(tab) {
  const isLogin = tab === 'login';
  document.getElementById('tabLogin').classList.toggle('active', isLogin);
  document.getElementById('tabRegister').classList.toggle('active', !isLogin);
  document.getElementById('loginForm').style.display = isLogin ? 'block' : 'none';
  document.getElementById('registerForm').style.display = isLogin ? 'none' : 'block';
}

async function handleAuth(type) {
  const user = document.getElementById(type === 'login' ? 'loginUser' : 'regUser').value;
  const pass = document.getElementById(type === 'login' ? 'loginPass' : 'regPass').value;
  const email = type === 'register' ? document.getElementById('regEmail').value.trim() : null;

  if (type === 'register' && (!user || !pass || !email)) {
    showToast('Username, Email, and Password are required!');
    return;
  }
  if (!user || !pass) { showToast('Fill in all fields!'); return; }
  if (type === 'register' && !email.includes('@')) { showToast('Please enter a valid email for registration!'); return; }

  try {
    const endpoint = type === 'login' ? '/api/auth/login' : '/api/auth/register';
    const payload = { username: user, password: pass };
    if (type === 'register') {
      payload.email = email;
    }
    

    const res = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(payload)
    });

    if (!res.ok) {
      const errorData = await res.json().catch(() => ({}));
      console.error('Authentication error:', errorData);
      throw new Error(errorData.error || errorData.message || `Server Error (${res.status})`);
    }
    const data = await res.json();

    currentUser = data.username;
    currentEmail = data.email || '';

    updateAuthUI();
    toggleAuthModal();

    if (type === 'login') {
      showToast(`Welcome back, ${user}!`);
    } else {
      showToast(`Account created! Welcome, ${user}!`);
    }
  } catch (e) {
    showToast('Error: ' + e.message);
  }
}

function updateAuthUI() {
  const section = document.getElementById('userSection');
  const checkoutBtn = document.getElementById('checkoutBtn');
  if (currentUser) {
    section.innerHTML = `
      <div class="user-info">
        <span>👤 ${escapeHtml(currentUser)}</span>
        <button class="logout-btn" style="color:var(--accent); text-decoration:none;" onclick="viewOrders()">My Orders</button>
        <button class="logout-btn" onclick="logout()">Logout</button>
      </div>`;
  } else {
    section.innerHTML = `<button class="auth-trigger-btn" onclick="toggleAuthModal()"><span>🔑</span> Login / Register</button>`;
  }
}

function toggleOrdersModal() {
  document.getElementById('ordersOverlay').classList.toggle('open');
}

async function viewOrders() {
  toggleOrdersModal();
  const container = document.getElementById('ordersList');
  container.innerHTML = '<div class="spinner"></div>';

  try {
    const res = await fetch('/api/orders/my', { headers: getAuthHeaders(), credentials: 'include' });
    if (!res.ok) throw new Error('Could not load orders');
    const data = await res.json();
    const orders = Array.isArray(data) ? data : (data.content ?? []);

    if (!orders.length) {
      container.innerHTML = '<div class="status">You haven\'t placed any orders yet.</div>';
      return;
    }

    container.innerHTML = orders.map(order => `
      <div class="order-history-item">
        <div style="display:flex; justify-content:space-between; margin-bottom:0.5rem;">
          <span style="font-weight:600; color:var(--accent);">#${order.id}</span>
          <span style="font-size:0.8rem; color:var(--muted);">${new Date(order.createdAt).toLocaleDateString()}</span>
        </div>
        <div style="font-size:0.85rem; margin-bottom:0.5rem;">
          ${order.items.map(item => `${escapeHtml(item.productName)} (x${item.quantity})`).join(', ')}
        </div>
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <span class="card-category" style="font-size:0.6rem;">${escapeHtml(order.status)}</span>
          <div>
            <button class="qty-btn" style="width:auto; padding:0 10px; display:inline-block; margin-right:10px;" onclick="viewOrderDetails(${order.id})">Details</button>
            <span style="font-family:'Bebas Neue'; font-size:1.2rem;">₴${order.totalAmount.toLocaleString()}</span>
          </div>
        </div>
      </div>
    `).join('');
  } catch (e) {
    container.innerHTML = `<div class="status">⚠️ ${e.message}</div>`;
  }
}

function toggleOrderDetailsModal() {
  document.getElementById('orderDetailsOverlay').classList.toggle('open');
}

async function viewOrderDetails(orderId) {
  toggleOrderDetailsModal();
  const container = document.getElementById('orderDetailsContent');
  document.getElementById('detailsTitle').textContent = `Order #${orderId}`;
  container.innerHTML = '<div class="spinner"></div>';

  try {
    const res = await fetch(`${API}/orders/${orderId}`, { headers: getAuthHeaders(), credentials: 'include' });
    if (!res.ok) throw new Error('Failed to load order details');
    const order = await res.json();

    container.innerHTML = `
      <div class="modal-summary" style="margin-top:0;">
        <div class="modal-summary-title">Purchased Items</div>
        ${order.items.map(item => `
          <div class="modal-summary-item">
            <span>${escapeHtml(item.productName)} (x${item.quantity})</span>
            <span>₴${(item.price * item.quantity).toLocaleString()}</span>
          </div>
        `).join('')}
        <div class="modal-total">
          <span class="modal-total-label">Total Amount</span>
          <span class="modal-total-val">₴${order.totalAmount.toLocaleString()}</span>
        </div>
      </div>
      <div style="font-size: 0.85rem; color: var(--muted);">
        <p><strong>Status:</strong> ${escapeHtml(order.status)}</p>
        <p><strong>Customer:</strong> ${escapeHtml(order.customerName)}</p>
        <p><strong>Email:</strong> ${escapeHtml(order.customerEmail)}</p>
      </div>
    `;
  } catch (e) {
    container.innerHTML = `<div class="status">⚠️ ${e.message}</div>`;
  }
}

async function logout() {
  try {
    await fetch(`${API}/auth/logout`, { method: 'POST', credentials: 'include' });
  } catch (e) {
    // network failure — clear local state anyway
  }
  currentUser = null;
  currentEmail = null;
  updateAuthUI();
  showToast('You have been logged out successfully.');
}

// ── PLACE ORDER ──
async function placeOrder() {
  const name  = document.getElementById('customerName').value.trim();
  const email = document.getElementById('customerEmail').value.trim();

  if (!name || !email) { showToast('Please fill in all fields!'); return; }
  if (!email.includes('@')) { showToast('Please enter a valid email!'); return; }

  const btn = document.getElementById('confirmBtn');
  btn.disabled = true;
  btn.textContent = 'Placing order...';
  
  const headers = getAuthHeaders();

  try {
    const res = await fetch(`${API}/orders`, {
      method: 'POST',
      headers: headers,
      credentials: 'include',
      body: JSON.stringify({
        sessionId: SESSION_ID,
        customerName: name,
        customerEmail: email
      })
    });

    if (res.status === 403) {
      showToast('Session expired or access denied. Please login again.');
      logout();
      return;
    }

    if (!res.ok) {
      const errorData = await res.json().catch(() => ({}));
      throw new Error(errorData.error || errorData.message || 'Order failed');
    }
    const order = await res.json();

    // Показуємо success екран
    document.getElementById('modalContent').innerHTML = `
      <div class="success-screen">
        <div class="success-icon">🎉</div>
        <h3>Order Placed!</h3>
        <p>Thank you, ${escapeHtml(name)}! Your order has been confirmed.</p>
        <div class="order-id">ORDER #${order.id}</div>
        <p>Confirmation will be sent to<br><strong>${escapeHtml(email)}</strong></p>
        <div style="margin-top:1.5rem">
          <button class="modal-confirm" style="width:100%" onclick="closeModal()">Continue Shopping →</button>
        </div>
      </div>
    `;

    // Очищаємо кошик
    cart = {};
    updateCartUI();
    loadProducts();

  } catch (e) {
    showToast('Error: ' + e.message);
    btn.disabled = false;
    btn.textContent = 'Place Order →';
  }
}

// ── TOAST ──
function showToast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 2500);
}

// ── INIT ──
// Зберігаємо шаблон форми при завантаженні сторінки
checkoutFormTemplate = document.getElementById('modalContent').innerHTML;
const logoutMsg = sessionStorage.getItem('logoutMessage');
if (logoutMsg) {
  showToast(logoutMsg);
  sessionStorage.removeItem('logoutMessage');
}
checkAuth();
loadProducts();
