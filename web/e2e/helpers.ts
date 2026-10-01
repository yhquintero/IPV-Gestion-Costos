import { expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

export const DEMO_PASSWORD = "Seed-Passw0rd!";

export async function login(page: Page, email: string) {
  await page.goto("/login");
  await page.getByLabel("Correo").fill(email);
  await page.getByLabel("Contraseña").fill(DEMO_PASSWORD);
  await page.getByTestId("login-submit").click();
  await page.waitForURL(/\/app\//);
}

export async function logout(page: Page) {
  await page.getByTestId("cerrar-sesion").click();
  await page.waitForURL(/\/login/);
}

export async function expectNoCriticalAxe(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
  const critical = results.violations.filter((v) => v.impact === "critical");
  expect(critical, JSON.stringify(critical, null, 2)).toEqual([]);
}
