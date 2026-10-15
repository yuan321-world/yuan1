import { defineConfig } from '@playwright/test'
const preview = process.env.E2E_PREVIEW === '1'
const port = process.env.E2E_PORT ? Number(process.env.E2E_PORT) : (preview ? 4173 : 5173)
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('E2E_PORT must be an integer from 1024 to 65535')
export default defineConfig({
  testDir: './tests/e2e', timeout: 30_000,
  forbidOnly: !!process.env.CI, retries: 0, workers: 2,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    browserName: 'chromium', headless: true,
    trace: 'off', screenshot: 'off',
  },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1440, height: 1000 } } },
    { name: 'mobile', use: { viewport: { width: 390, height: 844 } } },
  ],
  // Explicit mode for a separately started, controlled preview (avoids Windows child cleanup stalls).
  webServer: process.env.E2E_EXTERNAL_SERVER === '1' ? undefined : {
    command: preview ? `pnpm preview --port ${port}` : `pnpm dev --port ${port}`,
    url: `http://127.0.0.1:${port}`, reuseExistingServer: false,
  },
})

