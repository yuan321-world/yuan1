import { defineConfig } from '@playwright/test'
const preview = process.env.E2E_PREVIEW === '1'
const port = preview ? 4173 : 5173
export default defineConfig({
  testDir: './tests/e2e', timeout: 30_000,
  forbidOnly: !!process.env.CI, retries: 0, workers: 2,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    browserName: 'chromium', headless: true,
    trace: 'retain-on-failure', screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1440, height: 1000 } } },
    { name: 'mobile', use: { viewport: { width: 390, height: 844 } } },
  ],
  webServer: {
    command: preview ? 'pnpm preview' : 'pnpm dev',
    url: `http://127.0.0.1:${port}`, reuseExistingServer: false,
  },
})

