const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  timeout: 30_000,
  retries: 2,
  reporter: 'list',

  use: {
    baseURL: 'http://localhost:8080',
    headless: true,
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  },

  projects: [
    { name: 'chromium', testDir: './e2e', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox',  testDir: './e2e', use: { ...devices['Desktop Firefox'] } },
    // Pure API tests via the `request` fixture — no browser, no `page`.
    { name: 'api', testDir: './api' }
  ]
});
