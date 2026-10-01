import { authedFetch } from "@/lib/backend";
import { RateLabel } from "@/components/RateLabel";

export default async function PlataformaTasasPage() {
  const status =
    ((await authedFetch("GET", "/api/v1/rates/status")).json as {
      provider?: string;
      d04?: string;
      state?: { last_outcome?: string; paused?: boolean; consecutive_failures?: number };
    }) ?? {};
  const current =
    ((await authedFetch("GET", "/api/v1/rates/current")).json as Array<{
      instrument: string;
      value: string;
      status: string;
      source: string;
      label: string;
    }>) ?? [];
  return (
    <div>
      <h1 className="font-serif text-3xl">Proveedor de tasas</h1>
      <RateLabel className="mt-2" />
      <p className="mt-2 text-forest">
        D-04 pendiente: no hay token ni llamada a tasas.eltoque.com. Modo SEED/MOCK. CI nunca pega a
        la API real.
      </p>
      <dl className="mt-6 grid max-w-xl gap-2 rounded-xl bg-paper p-4 shadow-card">
        <div>
          <dt className="text-sm text-forest">Proveedor</dt>
          <dd>{status.provider ?? "ELTOQUE"}</dd>
        </div>
        <div>
          <dt className="text-sm text-forest">Último resultado</dt>
          <dd>{status.state?.last_outcome ?? "SKIPPED"}</dd>
        </div>
        <div>
          <dt className="text-sm text-forest">Pausado</dt>
          <dd>{status.state?.paused ? "sí (401/422)" : "no"}</dd>
        </div>
      </dl>
      <table className="mt-6 min-w-full bg-paper text-left">
        <thead>
          <tr>
            <th className="px-3 py-2">Instrumento</th>
            <th className="px-3 py-2">Valor</th>
            <th className="px-3 py-2">Estado</th>
          </tr>
        </thead>
        <tbody>
          {current.map((r) => (
            <tr key={r.instrument} className="border-t">
              <th className="px-3 py-2" scope="row">
                {r.instrument}
              </th>
              <td className="px-3 py-2 font-mono">{r.value}</td>
              <td className="px-3 py-2 text-sm">
                {r.status} · {r.source}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
