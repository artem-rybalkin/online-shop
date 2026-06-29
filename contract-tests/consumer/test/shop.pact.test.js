const path = require('path');
const { PactV3, MatchersV3 } = require('@pact-foundation/pact');

const { like, eachLike, integer } = MatchersV3;

const provider = new PactV3({
  consumer: 'OnlineShopFrontend',
  provider: 'OnlineShopAPI',
  dir: path.resolve(__dirname, '../../pacts'),
});

describe('Products API contract', () => {
  test('GET /api/products returns a page of products', () => {
    provider
      .given('at least one product exists')
      .uponReceiving('a request for the product list')
      .withRequest({
        method: 'GET',
        path: '/api/products',
        query: { size: '1' },
      })
      .willRespondWith({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: {
          content: eachLike({
            id: integer(1),
            name: like('iPhone 14'),
            price: like(35000.0),
            stock: integer(50),
            category: like('Electronics'),
          }),
        },
      });

    return provider.executeTest(async (mockServer) => {
      const res = await fetch(`${mockServer.url}/api/products?size=1`);
      const body = await res.json();
      if (res.status !== 200) throw new Error(`expected 200, got ${res.status}`);
      if (!Array.isArray(body.content) || body.content.length === 0) {
        throw new Error('expected a non-empty content array');
      }
    });
  });

  test('GET /api/products/{id} returns 404 for a missing product', () => {
    provider
      .given('product 999999 does not exist')
      .uponReceiving('a request for a non-existent product')
      .withRequest({
        method: 'GET',
        path: '/api/products/999999',
      })
      .willRespondWith({
        status: 404,
      });

    return provider.executeTest(async (mockServer) => {
      const res = await fetch(`${mockServer.url}/api/products/999999`);
      if (res.status !== 404) throw new Error(`expected 404, got ${res.status}`);
    });
  });
});

describe('Cart API contract', () => {
  test('adding a product to the cart returns the cart item', () => {
    provider
      .given('product 1 exists with available stock')
      .uponReceiving('a request to add product 1 to a cart')
      .withRequest({
        method: 'POST',
        path: '/api/cart/contract-test-session/add',
        headers: { 'Content-Type': 'application/json' },
        body: { productId: 1, quantity: 1 },
      })
      .willRespondWith({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: {
          id: integer(1),
          sessionId: like('contract-test-session'),
          quantity: integer(1),
          product: like({ id: 1 }),
        },
      });

    return provider.executeTest(async (mockServer) => {
      const res = await fetch(`${mockServer.url}/api/cart/contract-test-session/add`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId: 1, quantity: 1 }),
      });
      const body = await res.json();
      if (res.status !== 200) throw new Error(`expected 200, got ${res.status}`);
      if (body.quantity !== 1) throw new Error('expected quantity to be 1');
    });
  });
});

describe('Orders API contract', () => {
  test('getting an order with the wrong sessionId returns 403', () => {
    provider
      .given('order 1 exists and belongs to a different session')
      .uponReceiving('a request for order 1 with a non-owning sessionId')
      .withRequest({
        method: 'GET',
        path: '/api/orders/1',
        query: { sessionId: 'someone-elses-session' },
      })
      .willRespondWith({
        status: 403,
      });

    return provider.executeTest(async (mockServer) => {
      const res = await fetch(`${mockServer.url}/api/orders/1?sessionId=someone-elses-session`);
      if (res.status !== 403) throw new Error(`expected 403, got ${res.status}`);
    });
  });
});
