import { expect, test } from "@playwright/test";
import { expectNoCriticalAxe, login, logout } from "./helpers";

test.describe.configure({ mode: "serial" });

test("borrador → vigente → control IPV (cuatro ojos)", async ({ page }) => {
  await login(page, "costeador@alpha.test");
  await expect(page.getByRole("heading", { name: "Inicio" })).toBeVisible();
  await expectNoCriticalAxe(page);

  await page.goto("/app/catalogo");
  await expect(page.getByRole("heading", { name: "Catálogo" })).toBeVisible();
  await expect(page.getByText("Pizza sintética")).toBeVisible();

  await page.goto("/app/valores-ipv");
  await expect(page.getByRole("heading", { name: "Valores IPV" })).toBeVisible();
  await expect(page.getByText("SEED-INV-001")).toBeVisible();

  await page.goto("/app/fichas");
  await page.getByTestId("crear-ficha").click();
  await expect(page.getByTestId("ficha-status")).toContainText("Borrador");
  await expect(page.getByTestId("ficha-status")).toContainText("10.05");
  await page.getByTestId("enviar").click();
  await expect(page.getByTestId("ficha-status")).toContainText("EN_REVISION");
  await logout(page);

  await login(page, "revisor@alpha.test");
  await page.goto("/app/fichas");
  await page.locator('[data-testid="ficha-row"][data-status="EN_REVISION"]').first().click();
  await page.getByTestId("validar").click();
  await expect(page.getByTestId("ficha-status")).toContainText("VALIDADA");
  await logout(page);

  await login(page, "aprobador@alpha.test");
  await page.goto("/app/fichas");
  await page.locator('[data-testid="ficha-row"][data-status="VALIDADA"]').first().click();
  await page.getByTestId("aprobar").click();
  await expect(page.getByTestId("ficha-status")).toContainText("APROBADA");
  await page.getByTestId("activar").click();
  await expect(page.getByTestId("ficha-status")).toContainText("VIGENTE");

  await page.goto("/app/controles");
  await page.getByTestId("crear-control").click();
  await expect(page.getByTestId("control-status")).toContainText("VALIDADO");
  await logout(page);

  await login(page, "admin@alpha.test");
  await page.goto("/app/auditoria");
  await expect(page.getByTestId("audit-verify")).toContainText("íntegra");
  await expectNoCriticalAxe(page);
});
