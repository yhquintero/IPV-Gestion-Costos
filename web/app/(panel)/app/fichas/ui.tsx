"use client";

import { useCallback, useEffect, useState } from "react";
import { bff, type Me } from "@/lib/client";

type Sheet = {
  id: string;
  code: string;
  current_version?: { id: string; status: string; total_cost?: number; etag?: number };
};

type Version = {
  id: string;
  cost_sheet_id: string;
  status: string;
  total_cost?: number;
  unit_cost?: number;
  lines?: unknown[];
};

const COMPANY = "11111111-1111-7000-8000-000000000010";
const BRANCH = "11111111-1111-7000-8000-000000000020";
const PRODUCT = "11111111-1111-7000-8000-000000000070";
const MATERIAL = "11111111-1111-7000-8000-000000000060";
const IPV = "11111111-1111-7000-8000-000000000080";

export function FichasUi() {
  const [me, setMe] = useState<Me | null>(null);
  const [sheets, setSheets] = useState<Sheet[]>([]);
  const [current, setCurrent] = useState<Version | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const reload = useCallback(async () => {
    const { data } = await bff<Sheet[]>("GET", "/cost-sheets");
    setSheets(Array.isArray(data) ? data : []);
  }, []);

  useEffect(() => {
    void bff<Me>("GET", "/me").then((r) => setMe(r.data));
    void reload();
  }, [reload]);

  function can(perm: string) {
    return Boolean(me?.permissions.includes(perm));
  }

  async function select(s: Sheet) {
    if (!s.current_version) return;
    const res = await bff<Version>("GET", `/cost-sheets/${s.id}/versions/${s.current_version.id}`);
    if (res.status >= 400) {
      setMessage("No se pudo cargar la versión.");
      return;
    }
    setCurrent({ ...res.data, cost_sheet_id: s.id, id: s.current_version.id });
    setMessage(`Seleccionada ${s.code} · ${res.data.status}`);
  }

  async function crear() {
    setMessage(null);
    const code = `FC-WEB-${Date.now().toString().slice(-6)}`;
    const created = await bff<{ id: string; version_id: string }>("POST", "/cost-sheets", {
      company_id: COMPANY,
      product_id: PRODUCT,
      branch_id: BRANCH,
      code,
    });
    if (created.status >= 400) {
      setMessage("No se pudo crear la ficha.");
      return;
    }
    const patched = await bff<Version>("PATCH", `/cost-sheets/${created.data.id}/versions/${created.data.version_id}`, {
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
    });
    setCurrent({ ...patched.data, cost_sheet_id: created.data.id, id: created.data.version_id });
    await reload();
    setMessage(`Borrador ${code} listo. Subtotal de línea 10.05 CUP (HALF_UP).`);
  }

  async function act(path: string, body: Record<string, unknown> = {}) {
    if (!current) return;
    const res = await bff<Version>("POST", `/cost-sheets/${current.cost_sheet_id}/versions/${current.id}/${path}`, body);
    if (res.status >= 400) {
      setMessage(`Acción ${path} rechazada (${res.status}).`);
      return;
    }
    setCurrent({ ...res.data, cost_sheet_id: current.cost_sheet_id, id: current.id });
    await reload();
    setMessage(`Estado: ${res.data.status}`);
  }

  const status = current?.status;
  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-serif text-3xl">Fichas de costo</h1>
          <p className="text-forest">Ciclo borrador → revisión → validada → aprobada → vigente. Cuatro ojos en validar y aprobar.</p>
        </div>
        {can("costing:edit") ? (
          <button type="button" onClick={crear} className="rounded-full bg-forest px-4 py-2 text-sand" data-testid="crear-ficha">
            Crear borrador
          </button>
        ) : null}
      </div>
      {message ? (
        <p role="status" className="mt-4 rounded-lg bg-paper px-3 py-2 text-sm" data-testid="ficha-status">
          {message}
        </p>
      ) : null}
      <div className="mt-6 overflow-x-auto rounded-xl bg-paper shadow-card">
        <table className="min-w-full text-left text-sm">
          <thead className="bg-sand">
            <tr>
              <th className="px-3 py-2" scope="col">
                Código
              </th>
              <th className="px-3 py-2" scope="col">
                Estado
              </th>
              <th className="px-3 py-2" scope="col">
                Total
              </th>
            </tr>
          </thead>
          <tbody>
            {sheets.map((s) => (
              <tr
                key={s.id}
                className="cursor-pointer border-t border-ink/10 hover:bg-sand/60"
                data-testid="ficha-row"
                data-code={s.code}
                data-status={s.current_version?.status}
                onClick={() => void select(s)}
              >
                <th className="px-3 py-2 font-medium" scope="row">
                  {s.code}
                </th>
                <td className="px-3 py-2">{s.current_version?.status}</td>
                <td className="px-3 py-2 font-mono">{s.current_version?.total_cost ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {current ? (
        <div className="mt-6 flex flex-wrap gap-2">
          {status === "BORRADOR" && can("costing:submit") ? (
            <button type="button" className="rounded-full border px-3 py-1" onClick={() => act("submit")} data-testid="enviar">
              Enviar a revisión
            </button>
          ) : null}
          {status === "EN_REVISION" && can("costing:validate") ? (
            <button type="button" className="rounded-full border px-3 py-1" onClick={() => act("validate")} data-testid="validar">
              Validar
            </button>
          ) : null}
          {status === "VALIDADA" && can("costing:approve") ? (
            <button type="button" className="rounded-full border px-3 py-1" onClick={() => act("approve")} data-testid="aprobar">
              Aprobar
            </button>
          ) : null}
          {status === "APROBADA" && can("costing:activate") ? (
            <button
              type="button"
              className="rounded-full border px-3 py-1"
              onClick={() => act("activate", { valid_from: "2026-10-01" })}
              data-testid="activar"
            >
              Activar
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
