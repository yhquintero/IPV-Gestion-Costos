#!/usr/bin/env node
/** Corte vertical BFF (sin navegador). Complementa Playwright cuando hay Chromium. */

const BASE = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3000";
const PASS = "Seed-Passw0rd!";
const COMPANY = "11111111-1111-7000-8000-000000000010";
const BRANCH = "11111111-1111-7000-8000-000000000020";
const PRODUCT = "11111111-1111-7000-8000-000000000070";
const MATERIAL = "11111111-1111-7000-8000-000000000060";
const IPV = "11111111-1111-7000-8000-000000000080";

function cookieJar(setCookie) {
  const jar = new Map();
  for (const line of setCookie) {
    const [nv] = line.split(";");
    const eq = nv.indexOf("=");
    if (eq > 0) jar.set(nv.slice(0, eq).trim(), nv.slice(eq + 1).trim());
  }
  return jar;
}

function headerFrom(jar) {
  return [...jar.entries()].map(([k, v]) => `${k}=${v}`).join("; ");
}

async function req(method, path, { jar, body, json = true } = {}) {
  const headers = { Accept: "application/json" };
  if (jar) headers.Cookie = headerFrom(jar);
  const csrf = jar?.get("ipv_csrf");
  if (csrf && method !== "GET") headers["x-csrf-token"] = decodeURIComponent(csrf);
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    redirect: "manual",
  });
  const next = jar ? new Map(jar) : new Map();
  for (const [k, v] of cookieJar(res.headers.getSetCookie?.() ?? [])) next.set(k, v);
  let data = null;
  if (json && res.status !== 204 && res.status !== 302 && res.status !== 307) {
    data = await res.json().catch(() => null);
  }
  return { res, data, jar: next };
}

async function login(email) {
  const { res, data, jar } = await req("POST", "/api/bff/login", {
    body: { email, password: PASS },
  });
  if (res.status !== 200) throw new Error(`login ${email} → ${res.status} ${JSON.stringify(data)}`);
  if (!jar.get("ipv_access")) throw new Error("missing ipv_access cookie");
  if (JSON.stringify(data).includes("access_token")) throw new Error("JWT leaked to browser payload");
  return jar;
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}

const home = await fetch(`${BASE}/`);
assert(home.ok, `GET / ${home.status}`);
const csp = home.headers.get("content-security-policy") ?? "";
assert(csp.includes("default-src 'self'"), "CSP default-src");
assert(csp.includes("frame-ancestors 'none'"), "CSP frame-ancestors");
const precios = await fetch(`${BASE}/precios`);
assert(precios.ok, `GET /precios ${precios.status}`);
const preciosHtml = await precios.text();
assert(preciosHtml.includes("DATOS DE PRUEBA"), "rate disclaimer");
assert(preciosHtml.includes("CUP"), "CUP derived");

const gated = await fetch(`${BASE}/app/inicio`, { redirect: "manual" });
assert(gated.status === 307 || gated.status === 302, `gate ${gated.status}`);
assert((gated.headers.get("location") ?? "").includes("/login"), "redirect login");

let jar = await login("costeador@alpha.test");
const me = await req("GET", "/api/bff/me", { jar });
assert(me.res.status === 200 && me.data.email === "costeador@alpha.test", "me costeador");

const created = await req("POST", "/api/bff/cost-sheets", {
  jar,
  body: { company_id: COMPANY, product_id: PRODUCT, branch_id: BRANCH, code: `FC-BFF-${Date.now()}` },
});
assert(created.res.status === 201, `create ${created.res.status}`);
jar = created.jar;
const sheetId = created.data.id;
const versionId = created.data.version_id;

const patched = await req("PATCH", `/api/bff/cost-sheets/${sheetId}/versions/${versionId}`, {
  jar,
  body: {
    yield_qty: 2,
    calc_currency: "CUP",
    lines: [
      {
        line_type: "MATERIAL",
        raw_material_id: MATERIAL,
        ipv_value_id: IPV,
        quantity: 1.005,
        unit_cost_snapshot: 10,
        unit_currency: "CUP",
      },
    ],
  },
});
assert(patched.data.total_cost === 10.05, `HALF_UP ${patched.data.total_cost}`);

const submitted = await req("POST", `/api/bff/cost-sheets/${sheetId}/versions/${versionId}/submit`, { jar, body: {} });
assert(submitted.data.status === "EN_REVISION", `submit ${submitted.data.status}`);

jar = await login("revisor@alpha.test");
const fourEyes = await req("POST", `/api/bff/cost-sheets/${sheetId}/versions/${versionId}/validate`, {
  jar: await login("costeador@alpha.test"),
  body: {},
});
assert(fourEyes.res.status === 403, "author cannot validate");
const validated = await req("POST", `/api/bff/cost-sheets/${sheetId}/versions/${versionId}/validate`, { jar, body: {} });
assert(validated.data.status === "VALIDADA", `validate ${validated.data.status}`);

jar = await login("aprobador@alpha.test");
const approved = await req("POST", `/api/bff/cost-sheets/${sheetId}/versions/${versionId}/approve`, { jar, body: {} });
assert(approved.data.status === "APROBADA", `approve ${approved.data.status}`);
const activated = await req("POST", `/api/bff/cost-sheets/${sheetId}/versions/${versionId}/activate`, {
  jar,
  body: { valid_from: "2026-10-01" },
});
assert(activated.data.status === "VIGENTE", `activate ${activated.data.status}`);

const ctl = await req("POST", "/api/bff/ipv-controls", {
  jar,
  body: {
    company_id: COMPANY,
    branch_id: BRANCH,
    mode: "CONSISTENCIA",
    period_start: "2026-10-01",
    period_end: "2026-10-31",
  },
});
assert(ctl.res.status === 201 && ctl.data.control_no, `control ${ctl.res.status}`);
await req("POST", `/api/bff/ipv-controls/${ctl.data.id}/lines`, {
  jar,
  body: {
    product_id: PRODUCT,
    cost_sheet_version_id: versionId,
    expected_qty: 2,
    expected_unit_cost: 5.03,
    observed_qty: 2,
    observed_unit_cost: 5.03,
    observation_source: "MANUAL",
  },
});
const closed = await req("POST", `/api/bff/ipv-controls/${ctl.data.id}/close`, { jar, body: {} });
assert(closed.data.status === "VALIDADO", `close ${closed.data.status}`);

jar = await login("admin@alpha.test");
const verify = await req("GET", "/api/bff/audit/verify", { jar });
assert(verify.data.ok === true, "audit verify");

console.log("bff-flow ok", { sheetId, control_no: ctl.data.control_no, events: verify.data.events });
