const path = require('path');
const { PactV3, MatchersV3 } = require('@pact-foundation/pact');

const { like } = MatchersV3;

const provider = new PactV3({
  consumer: 'OnlineShopFrontend',
  provider: 'OnlineShopAPI',
  dir: path.resolve(__dirname, '../../pacts'),
});

describe('Auth API contract', () => {
  test('login with valid credentials returns the user', () => {
    provider
      .given('the admin user exists')
      .uponReceiving('a login request with valid credentials')
      .withRequest({
        method: 'POST',
        path: '/api/auth/login',
        headers: { 'Content-Type': 'application/json' },
        body: { username: 'admin', password: 'admin' },
      })
      .willRespondWith({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: {
          username: like('admin'),
          email: like('admin@shop.local'),
        },
      });

    return provider.executeTest(async (mockServer) => {
      const res = await fetch(`${mockServer.url}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'admin', password: 'admin' }),
      });
      const body = await res.json();
      if (res.status !== 200) throw new Error(`expected 200, got ${res.status}`);
      if (!body.username) throw new Error('expected a username in the response');
    });
  });

  test('login with wrong credentials returns 401', () => {
    provider
      .given('a user does not exist with this username/password combination')
      .uponReceiving('a login request with invalid credentials')
      .withRequest({
        method: 'POST',
        path: '/api/auth/login',
        headers: { 'Content-Type': 'application/json' },
        body: { username: 'nonexistent-contract-user', password: 'wrong' },
      })
      .willRespondWith({
        status: 401,
        headers: { 'Content-Type': 'application/json' },
        body: { message: like('Invalid username or password') },
      });

    return provider.executeTest(async (mockServer) => {
      const res = await fetch(`${mockServer.url}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'nonexistent-contract-user', password: 'wrong' }),
      });
      if (res.status !== 401) throw new Error(`expected 401, got ${res.status}`);
    });
  });

  test('GET /api/auth/me without a session returns 401', () => {
    provider
      .given('no user is authenticated')
      .uponReceiving('a request for the current user with no auth cookie')
      .withRequest({
        method: 'GET',
        path: '/api/auth/me',
      })
      .willRespondWith({
        status: 401,
      });

    return provider.executeTest(async (mockServer) => {
      const res = await fetch(`${mockServer.url}/api/auth/me`);
      if (res.status !== 401) throw new Error(`expected 401, got ${res.status}`);
    });
  });
});
