import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './tests/prototype', timeout: 15_000, expect: { timeout: 3_000 },
  workers: 2, reporter: 'list',
  use: { browserName: 'chromium', headless: true, actionTimeout: 4_000 },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1440, height: 1000 } } },
    { name: 'tablet', use: { viewport: { width: 582, height: 668 } } },
    { name: 'mobile', use: { viewport: { width: 390, height: 844 } } },
  ],
})
