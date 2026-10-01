import ratesFile from "@/data/exchange-rates-test.json";

type User = {
  id: string;
  organization_id: string;
  email: string;
  password: string;
  display_name: string;
  roles: string[];
  permissions: string[];
};

type Sheet = {
  id: string;
  code: string;
  product_id: string;
  company_id: string;
  version_id: string;
  version_no: number;
  status: string;
  etag: number;
  yield_qty: number | null;
  total_cost: number | null;
  unit_cost: number | null;
  created_by: string;
  content_hash: string | null;
  lines: Array<{
    line_no: number;
    line_type: string;
    raw_material_id?: string;
    ipv_value_id?: string;
    quantity: number;
    unit_cost_snapshot: number;
    unit_currency: string;
    line_cost: number;
  }>;
};

type Control = {
  id: string;
  control_no: string;
  status: string;
  mode: string;
  period_start: string;
  period_end: string;
  lines: unknown[];
};

const ORG_A = "11111111-1111-7000-8000-000000000001";
const ORG_B = "22222222-2222-7000-8000-000000000001";
const COMPANY = "11111111-1111-7000-8000-000000000010";
const BRANCH = "11111111-1111-7000-8000-000000000020";
const CATEGORY = "11111111-1111-7000-8000-000000000050";
const MATERIAL = "11111111-1111-7000-8000-000000000060";
const PRODUCT = "11111111-1111-7000-8000-000000000070";
const IPV = "11111111-1111-7000-8000-000000000080";

const ALL_PERMS = [
  "costs:view",
  "catalog:edit",
  "costing:edit",
  "costing:submit",
  "costing:validate",
  "costing:approve",
  "costing:activate",
  "costing:annul",
  "ipvcontrol:capture",
  "ipvcontrol:close",
  "rates:manual",
  "ADVANCED_AUDIT",
  "users:manage",
];

function user(
  id: string,
  org: string,
  email: string,
  name: string,
  roles: string[],
  permissions: string[],
): User {
  return {
    id,
    organization_id: org,
    email,
    password: "Seed-Passw0rd!",
    display_name: name,
    roles,
    permissions,
  };
}

const users: User[] = [
  user("u-admin-a", ORG_A, "admin@alpha.test", "Admin Alpha", ["ORG_ADMIN"], ALL_PERMS),
  user("u-cost-a", ORG_A, "costeador@alpha.test", "Costeador Alpha", ["COSTEADOR"], [
    "catalog:edit",
    "costing:edit",
    "costing:submit",
    "costs:view",
  ]),
  user("u-rev-a", ORG_A, "revisor@alpha.test", "Revisor Alpha", ["REVISOR"], [
    "costing:validate",
    "costs:view",
  ]),
  user("u-apr-a", ORG_A, "aprobador@alpha.test", "Aprobador Alpha", ["APROBADOR"], [
    "costing:approve",
    "costing:activate",
    "costing:annul",
    "costs:view",
    "ipvcontrol:close",
  ]),
  user("u-admin-b", ORG_B, "admin@beta.test", "Admin Beta", ["ORG_ADMIN"], ALL_PERMS),
  user("u-plat", ORG_A, "plataforma@alpha.test", "Plataforma", ["PLATFORM_ADMIN"], ALL_PERMS),
];

const tokens = new Map<string, string>(); // token -> userId
const sheets = new Map<string, Sheet>();
const controls = new Map<string, Control>();
const priceCatalog = [
  { id: "price-trial", policy_code: "IPV-TRIAL-7D", kind: "LICENSE", duration_days: 7, price_usd: 25 },
  { id: "price-m", policy_code: "IPV-MENSUAL", kind: "LICENSE", duration_days: 30, price_usd: 25 },
  { id: "price-t", policy_code: "IPV-TRIMESTRAL", kind: "LICENSE", duration_days: 90, price_usd: 75 },
  { id: "price-s", policy_code: "IPV-SEMESTRAL", kind: "LICENSE", duration_days: 180, price_usd: 195 },
  { id: "price-a", policy_code: "IPV-ANUAL", kind: "LICENSE", duration_days: 365, price_usd: 360 },
  { id: "price-b", policy_code: "IPV-BIENAL", kind: "LICENSE", duration_days: 730, price_usd: 600 },
  { id: "price-y", policy_code: "IPV-TRIENAL", kind: "LICENSE", duration_days: 1095, price_usd: 1020 },
];
type Contract = { id: string; organization_id: string; number: string; type: string; status: string; total_usd: number };
const contracts: Contract[] = [];
const receipts: Array<{ id: string; organization_id: string; number: string; payment_id: string; amount: number; currency: string }> = [];
const mockLicenses = new Map<
  string,
  {
    id: string;
    organization_id: string;
    user_id: string;
    policy_code: string;
    status: string;
    expires_at: string | null;
    entitlements: string[];
    max_devices: number;
  }
>();
const webhookIds = new Set<string>();
const products = [
  { id: PRODUCT, company_id: COMPANY, category_id: CATEGORY, kind: "PRODUCT", code: "PIZZA", name: "Pizza sintética", organization_id: ORG_A },
];
let seq = 1;
const audit: Array<{ action: string; org: string }> = [];

function jsonOk(json: unknown, status = 200) {
  return { status, json };
}
function problem(status: number, code: string, detail: string) {
  return { status, json: { type: "about:blank", status, code, detail } };
}

function actor(token?: string): User | undefined {
  if (!token) return undefined;
  const id = tokens.get(token);
  return users.find((u) => u.id === id);
}

function roundCent(n: number): number {
  return Math.round(n * 100) / 100;
}

export function mockHandle(
  method: string,
  path: string,
  body?: unknown,
  token?: string,
): { status: number; json: unknown } {
  const [pathname] = path.split("?");
  const p = pathname.replace(/\/$/, "") || "/";
  const user = actor(token);

  if (method === "POST" && p === "/api/v1/auth/login") {
    const b = (body ?? {}) as { email?: string; password?: string };
    const found = users.find((u) => u.email === b.email?.trim().toLowerCase());
    if (!found || found.password !== b.password) {
      return problem(401, "invalid_credentials", "invalid credentials");
    }
    const access = `mock.${found.id}.${++seq}`;
    tokens.set(access, found.id);
    audit.push({ action: "AUTH.LOGIN.SUCCESS", org: found.organization_id });
    return jsonOk({
      access_token: access,
      refresh_token: `refresh.${found.id}`,
      token_type: "Bearer",
      expires_in: 900,
      user: {
        id: found.id,
        organization_id: found.organization_id,
        email: found.email,
        display_name: found.display_name,
        roles: found.roles,
        permissions: found.permissions,
      },
    });
  }

  if (method === "POST" && p === "/api/v1/auth/logout") return { status: 204, json: null };

  if (!user) return problem(401, "invalid_credentials", "invalid token");

  if (method === "GET" && p === "/api/v1/me") {
    return jsonOk({
      id: user.id,
      organization_id: user.organization_id,
      email: user.email,
      display_name: user.display_name,
      roles: user.roles,
      permissions: user.permissions,
    });
  }

  if (method === "GET" && p === "/api/v1/organizations") {
    const all = [
      { id: ORG_A, name: "Alpha", status: "ACTIVE" },
      { id: ORG_B, name: "Beta", status: "ACTIVE" },
    ];
    if (user.roles.includes("PLATFORM_ADMIN")) return jsonOk(all);
    return jsonOk(all.filter((o) => o.id === user.organization_id));
  }

  if (method === "GET" && p === "/api/v1/users") {
    if (!user.permissions.includes("users:manage") && !user.roles.includes("PLATFORM_ADMIN")) {
      return problem(403, "forbidden", "missing permission users:manage");
    }
    return jsonOk(
      users
        .filter((u) => user.roles.includes("PLATFORM_ADMIN") || u.organization_id === user.organization_id)
        .map((u) => ({
          id: u.id,
          email: u.email,
          display_name: u.display_name,
          roles: u.roles,
          organization_id: u.organization_id,
        })),
    );
  }

  if (method === "GET" && p === "/api/v1/companies") {
    if (user.organization_id !== ORG_A) {
      return jsonOk([{ id: "22222222-2222-7000-8000-000000000010", name: "Beta S.A.", base_currency: "CUP" }]);
    }
    return jsonOk([{ id: COMPANY, name: "Alpha S.A.", tax_id: "ALPHA-00", base_currency: "CUP", timezone: "America/Havana" }]);
  }

  if (method === "GET" && p === "/api/v1/branches") {
    return jsonOk([{ id: BRANCH, company_id: COMPANY, code: "HAB-01", name: "Habana Centro", active: true }]);
  }

  if (method === "GET" && p === "/api/v1/categories") {
    return jsonOk([{ id: CATEGORY, company_id: COMPANY, code: "COM", name: "Comidas", kind: "COMIDAS" }]);
  }

  if (method === "GET" && p === "/api/v1/raw-materials") {
    return jsonOk([{ id: MATERIAL, company_id: COMPANY, category_id: CATEGORY, code: "HARINA", name: "Harina de trigo", active: true }]);
  }

  if (method === "GET" && p === "/api/v1/products") {
    return jsonOk(products.filter((x) => x.organization_id === user.organization_id));
  }

  if (method === "GET" && p.startsWith("/api/v1/products/")) {
    const id = p.split("/").pop();
    const found = products.find((x) => x.id === id && x.organization_id === user.organization_id);
    return found ? jsonOk(found) : problem(404, "not_found", "product not found");
  }

  if (method === "POST" && p === "/api/v1/products") {
    const b = body as { code: string; name: string; company_id: string; category_id: string; kind: string };
    const id = `prod-${++seq}`;
    const row = { id, organization_id: user.organization_id, ...b };
    products.push(row);
    return jsonOk({ id }, 201);
  }

  if (method === "GET" && p === "/api/v1/ipv-values") {
    if (!user.permissions.includes("costs:view")) return problem(403, "forbidden", "missing permission costs:view");
    return jsonOk([
      {
        id: IPV,
        company_id: COMPANY,
        raw_material_id: MATERIAL,
        currency: "CUP",
        unit_price: 45,
        valid_from: "2026-01-01",
        source_ref: "SEED-INV-001",
      },
    ]);
  }

  if (method === "GET" && p === "/api/v1/cost-sheets") {
    return jsonOk(
      [...sheets.values()]
        .filter(() => user.organization_id === ORG_A)
        .map((s) => ({
          id: s.id,
          code: s.code,
          product_id: s.product_id,
          company_id: s.company_id,
          current_version: {
            id: s.version_id,
            version_no: s.version_no,
            status: s.status,
            etag: s.etag,
            total_cost: s.total_cost,
            unit_cost: s.unit_cost,
          },
        })),
    );
  }

  if (method === "POST" && p === "/api/v1/cost-sheets") {
    const b = body as { code: string; product_id: string; company_id: string };
    const id = `sheet-${++seq}`;
    const versionId = `ver-${seq}`;
    const sheet: Sheet = {
      id,
      code: b.code,
      product_id: b.product_id,
      company_id: b.company_id,
      version_id: versionId,
      version_no: 1,
      status: "BORRADOR",
      etag: 1,
      yield_qty: null,
      total_cost: null,
      unit_cost: null,
      created_by: user.id,
      content_hash: null,
      lines: [],
    };
    sheets.set(id, sheet);
    audit.push({ action: "COSTING.VERSION.CREATE", org: user.organization_id });
    return jsonOk({ id, version_id: versionId, status: "BORRADOR" }, 201);
  }

  const versionMatch = p.match(/^\/api\/v1\/cost-sheets\/([^/]+)\/versions\/([^/]+)(?:\/(\w+))?$/);
  if (versionMatch) {
    const sheet = sheets.get(versionMatch[1]);
    if (!sheet || sheet.version_id !== versionMatch[2]) return problem(404, "not_found", "cost_sheet_version not found");
    const action = versionMatch[3];
    if (method === "GET" && !action) {
      return jsonOk({
        id: sheet.version_id,
        cost_sheet_id: sheet.id,
        status: sheet.status,
        etag: sheet.etag,
        yield_qty: sheet.yield_qty,
        total_cost: sheet.total_cost,
        unit_cost: sheet.unit_cost,
        lines: sheet.lines,
      });
    }
    if (method === "PATCH" && !action) {
      const b = body as { yield_qty?: number; lines?: Sheet["lines"] };
      if (sheet.status !== "BORRADOR") return problem(409, "frozen", "only BORRADOR can be edited");
      if (b.yield_qty != null) sheet.yield_qty = Number(b.yield_qty);
      if (b.lines) {
        sheet.lines = b.lines.map((ln, i) => {
          const lineCost = roundCent(Number(ln.quantity) * Number(ln.unit_cost_snapshot));
          return { ...ln, line_no: i + 1, line_cost: lineCost };
        });
        sheet.total_cost = roundCent(sheet.lines.reduce((s, l) => s + l.line_cost, 0));
        sheet.unit_cost = sheet.yield_qty ? roundCent(sheet.total_cost / sheet.yield_qty) : null;
      }
      sheet.etag += 1;
      return jsonOk({ ...sheet, lines: sheet.lines, id: sheet.version_id, cost_sheet_id: sheet.id });
    }
    const transitions: Record<string, { next: string; perm: string; fourEyes?: boolean }> = {
      submit: { next: "EN_REVISION", perm: "costing:submit" },
      validate: { next: "VALIDADA", perm: "costing:validate", fourEyes: true },
      approve: { next: "APROBADA", perm: "costing:approve", fourEyes: true },
      activate: { next: "VIGENTE", perm: "costing:activate" },
      annul: { next: "ANULADA", perm: "costing:annul" },
    };
    if (method === "POST" && action && action in transitions) {
      const t = transitions[action];
      if (!user.permissions.includes(t.perm)) return problem(403, "forbidden", `missing permission ${t.perm}`);
      if (t.fourEyes && user.id === sheet.created_by) return problem(403, "four_eyes", "author cannot validate or approve");
      if (action === "submit") {
        if (!sheet.yield_qty || sheet.lines.length === 0) {
          return problem(422, "rules_failed", "blocking rules failed");
        }
        sheet.content_hash = "mock-hash";
      }
      sheet.status = t.next;
      sheet.etag += 1;
      audit.push({ action: `COSTING.VERSION.${action.toUpperCase()}`, org: user.organization_id });
      return jsonOk({ id: sheet.version_id, cost_sheet_id: sheet.id, status: sheet.status, etag: sheet.etag, lines: sheet.lines, total_cost: sheet.total_cost });
    }
  }

  if (method === "GET" && p === "/api/v1/ipv-controls") {
    return jsonOk([...controls.values()]);
  }
  if (method === "POST" && p === "/api/v1/ipv-controls") {
    const b = body as { period_start: string; period_end: string; mode?: string };
    const id = `ctl-${++seq}`;
    const control: Control = {
      id,
      control_no: `IPV-2026-${String(seq).padStart(4, "0")}`,
      status: "PENDIENTE",
      mode: b.mode ?? "CONSISTENCIA",
      period_start: b.period_start,
      period_end: b.period_end,
      lines: [],
    };
    controls.set(id, control);
    return jsonOk({ id, control_no: control.control_no, status: control.status }, 201);
  }
  const cl = p.match(/^\/api\/v1\/ipv-controls\/([^/]+)\/(lines|close)$/);
  if (cl) {
    const control = controls.get(cl[1]);
    if (!control) return problem(404, "not_found", "control not found");
    if (cl[2] === "lines" && method === "POST") {
      control.lines.push(body);
      control.status = "EN_PROCESO";
      return jsonOk({ id: `line-${++seq}` });
    }
    if (cl[2] === "close" && method === "POST") {
      control.status = "VALIDADO";
      return jsonOk({ id: control.id, status: control.status });
    }
  }

  if (method === "GET" && p === "/api/v1/rates/current") {
    const rows = Object.entries(ratesFile.instruments).map(([instrument, value]) => ({
      instrument,
      value,
      status: "TEST",
      source: "SEED_TEST",
      is_test: true,
      label: "DATOS DE PRUEBA · Tasa de referencia, no oficial",
    }));
    return jsonOk(rows);
  }

  if (method === "GET" && p === "/api/v1/audit/verify") {
    return jsonOk({ ok: true, events: audit.filter((a) => a.org === user.organization_id).length, blocks: 1, broken_event_seq: [] });
  }
  if (method === "GET" && p === "/api/v1/audit") {
    return jsonOk(audit.filter((a) => a.org === user.organization_id).map((a, i) => ({ seq: i + 1, action: a.action, result: "SUCCESS" })));
  }
  if (method === "GET" && p === "/api/v1/notifications") return jsonOk([]);
  if (method === "GET" && p === "/api/v1/price-catalog") return jsonOk(priceCatalog);
  if (method === "GET" && p === "/api/v1/contracts") {
    return jsonOk(contracts.filter((c) => c.organization_id === user.organization_id));
  }
  if (method === "POST" && p === "/api/v1/contracts") {
    const b = body as { number: string; type?: string };
    const row: Contract = {
      id: `ctr-${++seq}`,
      organization_id: user.organization_id,
      number: b.number,
      type: b.type ?? "LICENSE",
      status: "DRAFT",
      total_usd: 0,
    };
    contracts.push(row);
    return jsonOk(row);
  }
  if (method === "GET" && p === "/api/v1/receipts") {
    return jsonOk(receipts.filter((r) => r.organization_id === user.organization_id));
  }
  if (method === "GET" && p === "/api/v1/licenses/me") {
    const mine = [...mockLicenses.values()].find(
      (l) => l.organization_id === user.organization_id && l.user_id === user.id,
    );
    if (!mine) return jsonOk({ status: "NOT_ACTIVATED", entitlements: [] });
    return jsonOk({
      id: mine.id,
      policy_code: mine.policy_code,
      status: mine.status,
      blocks_access: ["EXPIRED", "REVOKED", "DISCONNECTED", "DEVICE_LIMIT", "NOT_ACTIVATED", "SUSPENDED"].includes(
        mine.status,
      ),
      expires_at: mine.expires_at,
      entitlements: mine.entitlements,
      max_devices: mine.max_devices,
    });
  }
  if (method === "GET" && p === "/api/v1/licenses") {
    return jsonOk(
      [...mockLicenses.values()]
        .filter((l) => l.organization_id === user.organization_id)
        .map((l) => ({
          id: l.id,
          user_id: l.user_id,
          policy_code: l.policy_code,
          status: l.status,
          expires_at: l.expires_at,
          max_devices: l.max_devices,
        })),
    );
  }
  if (method === "POST" && p === "/api/v1/licenses") {
    const b = body as { user_id: string; policy_code: string; max_devices?: number };
    const id = `lic-${++seq}`;
    mockLicenses.set(id, {
      id,
      organization_id: user.organization_id,
      user_id: b.user_id,
      policy_code: b.policy_code,
      status: "INACTIVE",
      expires_at: null,
      entitlements: ["IPV_BASIC", "COST_SHEETS", "REPORTS", "ANDROID_ACCESS", "WEB_ACCESS"],
      max_devices: b.max_devices ?? 2,
    });
    return jsonOk({ id, status: "INACTIVE", policy_code: b.policy_code });
  }
  if (method === "POST" && p === "/api/v1/webhooks/keygen") {
    const b = body as { id?: string; type?: string };
    if (!b.id) return problem(400, "missing_id", "event id required");
    const dup = webhookIds.has(b.id);
    webhookIds.add(b.id);
    return jsonOk({ id: b.id, type: b.type ?? "unknown", duplicate: dup, mode: "FAKE" });
  }
  if (method === "GET" && p === "/api/v1/reports/cost-sheets") {
    return jsonOk(
      [...sheets.values()].map((s) => ({
        code: s.code,
        product: products.find((p) => p.id === s.product_id)?.name ?? s.product_id,
        version_no: s.version_no,
        status: s.status,
        total_cost: s.total_cost,
        unit_cost: s.unit_cost,
      })),
    );
  }

  return problem(404, "not_found", `${method} ${p}`);
}
