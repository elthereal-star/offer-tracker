import { defineConfig, devices } from '@playwright/test'

const port = Number(process.env.E2E_PORT || 18080)
const baseURL = process.env.E2E_BASE_URL || `http://127.0.0.1:${port}`
const browserChannel = process.env.E2E_BROWSER_CHANNEL

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        ...(browserChannel ? { channel: browserChannel } : {})
      }
    }
  ],
  webServer: process.env.E2E_EXTERNAL_SERVER
    ? undefined
    : {
        command: 'java -jar ../target/offer-tracker-0.1.0.jar',
        url: baseURL,
        timeout: 120_000,
        reuseExistingServer: false,
        env: {
          SERVER_PORT: String(port),
          SPRING_DATASOURCE_URL: 'jdbc:h2:mem:offer-tracker-e2e;DB_CLOSE_DELAY=-1',
          SPRING_DATASOURCE_DRIVER_CLASS_NAME: 'org.h2.Driver',
          SPRING_H2_CONSOLE_ENABLED: 'false',
          JWT_SECRET: 'e2e-only-secret-at-least-32-characters'
        }
      }
})
