import { expect, test } from "@playwright/test";
import { expectNoCriticalAxe } from "./helpers";

test.describe("sitio público", () => {
  test("inicio, precios CUP derivado y CSP", async ({ page }) => {
    const res = await page.goto("/");
    expect(res?.ok()).toBeTruthy();
    const csp = res?.headers()["content-security-policy"] ?? "";
    expect(csp).toContain("default-src 'self'");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
    await expect(page.getByRole("heading", { name: /Fichas de costo/i })).toBeVisible();
    await expectNoCriticalAxe(page);

    await page.goto("/precios");
    await expect(page.getByRole("heading", { name: "Precios en USD" })).toBeVisible();
    await expect(page.getByRole("note")).toContainText("Tasa de referencia, no oficial");
    await expect(page.getByRole("note")).toContainText("DATOS DE PRUEBA");
    await expect(page.locator("table")).toContainText("CUP");
    await expectNoCriticalAxe(page);
  });

  test("panel sin sesión redirige a login", async ({ page }) => {
    await page.goto("/app/inicio");
    await page.waitForURL(/\/login/);
    await expect(page.getByRole("heading", { name: "Entrar al panel" })).toBeVisible();
    await expectNoCriticalAxe(page);
  });
});
