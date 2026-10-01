import { authedFetch } from "@/lib/backend";
import { formatUsd } from "@/lib/rates";

export default async function CatalogoPreciosPage() {
  const rows =
    ((await authedFetch("GET", "/api/v1/price-catalog")).json as Array<{
      id: string;
      policy_code: string;
      duration_days: number;
      price_usd: number;
    }>) ?? [];
  return (
    <div>
      <h1 className="font-serif text-3xl">Catálogo de precios</h1>
      <p className="mt-2 text-forest">
        Precio primario USD, editable sin código (D-08). El equivalente CUP se congela al cotizar
        (HALF_UP). No es tarifa legal.
      </p>
      <table className="mt-6 min-w-full border-collapse text-left">
        <thead className="bg-paper">
          <tr>
            <th className="px-3 py-2">Política</th>
            <th className="px-3 py-2">Días</th>
            <th className="px-3 py-2">USD</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className="border-b border-ink/10">
              <td className="px-3 py-2 font-mono">{r.policy_code}</td>
              <td className="px-3 py-2">{r.duration_days}</td>
              <td className="px-3 py-2 font-mono">{formatUsd(Number(r.price_usd))}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
