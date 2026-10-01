"use client";

import { useState } from "react";
import { bff } from "@/lib/client";

export function ControlesUi() {
  const [msg, setMsg] = useState<string | null>(null);
  const [no, setNo] = useState<string | null>(null);

  async function crear() {
    const created = await bff<{ id: string; control_no: string }>("POST", "/ipv-controls", {
      company_id: "11111111-1111-7000-8000-000000000010",
      branch_id: "11111111-1111-7000-8000-000000000020",
      mode: "CONSISTENCIA",
      period_start: "2026-10-01",
      period_end: "2026-10-31",
    });
    if (created.status >= 400) {
      setMsg("No se pudo crear el control.");
      return;
    }
    setNo(created.data.control_no);
    const sheets = await bff<Array<{ id: string; current_version?: { id: string; status: string } }>>("GET", "/cost-sheets");
    const vigente = (Array.isArray(sheets.data) ? sheets.data : []).find((s) => s.current_version?.status === "VIGENTE");
    if (vigente?.current_version) {
      await bff("POST", `/ipv-controls/${created.data.id}/lines`, {
        product_id: "11111111-1111-7000-8000-000000000070",
        cost_sheet_version_id: vigente.current_version.id,
        expected_qty: 2,
        expected_unit_cost: 5.03,
        observed_qty: 2,
        observed_unit_cost: 5.03,
        observation_source: "MANUAL",
      });
      const closed = await bff<{ status: string }>("POST", `/ipv-controls/${created.data.id}/close`, {});
      setMsg(`${created.data.control_no} · ${closed.data.status}`);
    } else {
      setMsg(`${created.data.control_no} creado. No hay ficha vigente para líneas.`);
    }
  }

  return (
    <div>
      <h1 className="font-serif text-3xl">Controles IPV</h1>
      <p className="text-forest">El número definitivo lo asigna el servidor (I-19).</p>
      <button type="button" className="mt-4 rounded-full bg-forest px-4 py-2 text-sand" onClick={crear} data-testid="crear-control">
        Registrar control del período
      </button>
      {msg ? (
        <p className="mt-4" role="status" data-testid="control-status">
          {msg}
        </p>
      ) : null}
      {no ? <p className="font-mono">{no}</p> : null}
    </div>
  );
}
