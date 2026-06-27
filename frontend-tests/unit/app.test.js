/**
 * Unit tests for app.js.
 * The DOM and the script itself are loaded once in setup.js via jsdom's
 * runScripts:'dangerously', so all app.js globals are on window.
 */

// ─── Helpers ─────────────────────────────────────────────────────────────────

const SAMPLE_PRODUCTS = [
  { id: 1, name: 'iPhone 14',   description: 'Apple phone', price: 35000, stock: 10, category: 'Electronics' },
  { id: 2, name: 'MacBook Air', description: 'Apple laptop', price: 45000, stock: 5,  category: 'Electronics' },
  { id: 3, name: 'Nike Air Max', description: 'Running shoes', price: 4000, stock: 0,  category: 'Clothing' },
  { id: 4, name: 'Java Book',   description: 'Programming',  price: 500,  stock: 20, category: 'Books' },
];

function mockPageResponse(products) {
  global.fetch = jest.fn(() =>
    Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ content: products, totalElements: products.length })
    })
  );
}

// ─── getImage ────────────────────────────────────────────────────────────────
describe('getImage', () => {
  test('returns iphone image URL for "iPhone 14"', () => {
    expect(window.getImage('iPhone 14')).toContain('663499482523');
  });

  test('returns macbook image URL for "MacBook Air"', () => {
    expect(window.getImage('MacBook Air')).toContain('1517336714731');
  });

  test('returns nike image URL for "Nike Air Max"', () => {
    expect(window.getImage('Nike Air Max')).toContain('1542291026');
  });

  test('returns sony image URL for "Sony WH-1000XM4"', () => {
    expect(window.getImage('Sony WH-1000XM4')).toContain('1505740420928');
  });

  test('returns default placeholder for unrecognised product', () => {
    const url = window.getImage('Completely Unknown Widget 9000');
    expect(url).toContain('1523275335684');
  });

  test('returns default placeholder for null', () => {
    expect(window.getImage(null)).toContain('1523275335684');
  });

  test('returns default placeholder for empty string', () => {
    expect(window.getImage('')).toContain('1523275335684');
  });
});

// ─── renderProducts ───────────────────────────────────────────────────────────
describe('renderProducts', () => {
  beforeEach(() => {
    document.getElementById('productGrid').innerHTML = '';
  });

  test('renders one .card per product', () => {
    window.renderProducts(SAMPLE_PRODUCTS);
    expect(document.querySelectorAll('.card')).toHaveLength(4);
  });

  test('shows product name and price in the card', () => {
    window.renderProducts([SAMPLE_PRODUCTS[0]]);
    const card = document.querySelector('.card');
    expect(card.innerHTML).toContain('iPhone 14');
    expect(card.querySelector('.card-price').textContent).toMatch(/35.000/);
  });

  test('out-of-stock product has disabled button with "Out of Stock" text', () => {
    window.renderProducts([SAMPLE_PRODUCTS[2]]); // Nike, stock=0
    const btn = document.querySelector('.add-btn');
    expect(btn.disabled).toBe(true);
    expect(btn.textContent).toContain('Out of Stock');
  });

  test('in-stock product has enabled "Add to Cart" button', () => {
    window.renderProducts([SAMPLE_PRODUCTS[0]]); // iPhone, stock=10
    const btn = document.querySelector('.add-btn');
    expect(btn.disabled).toBe(false);
    expect(btn.textContent).toContain('Add to Cart');
  });

  test('low-stock product adds "low" class to stock label', () => {
    window.renderProducts([{ ...SAMPLE_PRODUCTS[0], stock: 25 }]); // stock=25 ≥ 20 → not low
    expect(document.querySelector('.card-stock').classList.contains('low')).toBe(false);

    window.renderProducts([{ ...SAMPLE_PRODUCTS[0], stock: 5 }]); // stock=5 < 20 → low
    expect(document.querySelector('.card-stock').classList.contains('low')).toBe(true);
  });

  test('shows "No products found" message for empty list', () => {
    window.renderProducts([]);
    expect(document.getElementById('productGrid').innerHTML)
      .toContain('No products found');
  });
});

// ─── loadProducts — Page / array response handling ────────────────────────────
describe('loadProducts', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    document.getElementById('productGrid').innerHTML = '';
    document.getElementById('categoryFilters').innerHTML = '';
  });

  test('extracts .content array from a Spring Page response', async () => {
    mockPageResponse([SAMPLE_PRODUCTS[0]]);
    await window.loadProducts();
    expect(document.querySelectorAll('.card')).toHaveLength(1);
  });

  test('handles a plain array response for backward compatibility', async () => {
    global.fetch = jest.fn(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve([SAMPLE_PRODUCTS[0]])
      })
    );
    await window.loadProducts();
    expect(document.querySelectorAll('.card')).toHaveLength(1);
  });

  test('renders category filter buttons for loaded products', async () => {
    mockPageResponse(SAMPLE_PRODUCTS);
    await window.loadProducts();
    const filters = document.querySelectorAll('.filter-btn');
    // "All" + "Electronics" + "Clothing" + "Books" = 4
    expect(filters.length).toBeGreaterThanOrEqual(4);
  });

  test('shows error message on HTTP failure', async () => {
    global.fetch = jest.fn(() =>
      Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) })
    );
    await window.loadProducts();
    expect(document.getElementById('productGrid').innerHTML)
      .toContain('HTTP Error 500');
  });

  test('shows error message on network failure', async () => {
    global.fetch = jest.fn(() => Promise.reject(new Error('Network offline')));
    await window.loadProducts();
    expect(document.getElementById('productGrid').innerHTML)
      .toContain('Network offline');
  });
});

// ─── applyFilters ─────────────────────────────────────────────────────────────
describe('applyFilters', () => {
  beforeEach(async () => {
    jest.clearAllMocks();
    mockPageResponse(SAMPLE_PRODUCTS);
    await window.loadProducts(); // populates internal allProducts
  });

  test('shows all products when category is "All" and no search', () => {
    window.filterBy('All', document.querySelector('.filter-btn') || document.createElement('button'));
    expect(document.querySelectorAll('.card')).toHaveLength(4);
  });

  test('filters to only Electronics products', () => {
    // Simulate clicking the Electronics filter
    window.currentCategory = 'Electronics';
    window.searchQuery = '';
    window.applyFilters();
    expect(document.querySelectorAll('.card')).toHaveLength(2);
  });

  test('filters to only Clothing products', () => {
    window.currentCategory = 'Clothing';
    window.searchQuery = '';
    window.applyFilters();
    expect(document.querySelectorAll('.card')).toHaveLength(1);
  });

  test('search by name filters correctly (case-insensitive)', () => {
    window.currentCategory = 'All';
    window.searchQuery = 'iphone';
    window.applyFilters();
    expect(document.querySelectorAll('.card')).toHaveLength(1);
    expect(document.querySelector('.card-name').textContent).toContain('iPhone 14');
  });

  test('search by description filters correctly', () => {
    window.currentCategory = 'All';
    window.searchQuery = 'programming';
    window.applyFilters();
    expect(document.querySelectorAll('.card')).toHaveLength(1);
  });

  test('combined category + search returns narrowed results', () => {
    window.currentCategory = 'Electronics';
    window.searchQuery = 'macbook';
    window.applyFilters();
    expect(document.querySelectorAll('.card')).toHaveLength(1);
    expect(document.querySelector('.card-name').textContent).toContain('MacBook');
  });

  test('non-matching search shows "No products found"', () => {
    window.currentCategory = 'All';
    window.searchQuery = 'xyzzy_no_match_9999';
    window.applyFilters();
    expect(document.getElementById('productGrid').innerHTML)
      .toContain('No products found');
  });
});

// ─── updateCartUI ─────────────────────────────────────────────────────────────
describe('updateCartUI', () => {
  beforeEach(() => {
    // Reset cart by clearing it through the window reference
    window.cart = {};
    window.updateCartUI();
  });

  test('shows 0 count and empty-cart message for an empty cart', () => {
    expect(document.getElementById('cartCount').textContent).toBe('0');
    expect(document.getElementById('drawerItems').innerHTML).toContain('empty');
  });

  test('counts total items across multiple products', () => {
    window.cart = {
      1: { id: 1, name: 'iPhone',   price: 35000, qty: 2 },
      2: { id: 2, name: 'MacBook',  price: 45000, qty: 1 }
    };
    window.updateCartUI();
    expect(document.getElementById('cartCount').textContent).toBe('3');
  });

  test('calculates total amount correctly', () => {
    window.cart = {
      1: { id: 1, name: 'iPhone',   price: 35000, qty: 2 }, // 70 000
      2: { id: 2, name: 'MacBook',  price: 45000, qty: 1 }  // 45 000
    };
    window.updateCartUI();
    // Total = 115 000 → formatted with locale separator
    expect(document.getElementById('totalVal').textContent).toContain('115');
  });

  test('renders one .cart-item per cart entry', () => {
    window.cart = {
      1: { id: 1, name: 'iPhone', price: 35000, qty: 1 },
      2: { id: 2, name: 'Shoes',  price: 4000,  qty: 3 }
    };
    window.updateCartUI();
    expect(document.querySelectorAll('.cart-item')).toHaveLength(2);
  });
});

// ─── Auth modal toggle ────────────────────────────────────────────────────────
describe('toggleAuthModal', () => {
  test('adds "open" class when called on a closed modal', () => {
    document.getElementById('authOverlay').classList.remove('open');
    window.toggleAuthModal();
    expect(document.getElementById('authOverlay').classList.contains('open')).toBe(true);
  });

  test('removes "open" class when called on an open modal', () => {
    document.getElementById('authOverlay').classList.add('open');
    window.toggleAuthModal();
    expect(document.getElementById('authOverlay').classList.contains('open')).toBe(false);
  });
});

// ─── switchAuthTab ────────────────────────────────────────────────────────────
describe('switchAuthTab', () => {
  test('shows register form when "register" tab is selected', () => {
    window.switchAuthTab('register');
    expect(document.getElementById('registerForm').style.display).toBe('block');
    expect(document.getElementById('loginForm').style.display).toBe('none');
  });

  test('shows login form when "login" tab is selected', () => {
    window.switchAuthTab('login');
    expect(document.getElementById('loginForm').style.display).toBe('block');
    expect(document.getElementById('registerForm').style.display).toBe('none');
  });
});
