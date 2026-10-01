import { authedFetch } from "@/lib/backend";

export default async function ValoresIpvPage() {
  const { status, json } = await authedFetch("GET", "/api/v1/ipv-values");
  const rows = status === 200 ? (json as Array<{ id: string; currency: string; unit_price: number; source_ref?: string }>) : [];
  return (
    <div>
      <h1 className="font-serif text-3xl">Valores IPV</h1>
      <p className="mt-2 text-forest">Registro previo a la ficha. Requiere permiso de ver costos.</p>
      {status === 403 ? (
        <p role="alert">No tiene permiso para ver costos.</p>
      ) : (
        <table className="mt-6 min-w-full bg-paper text-left">
          <thead>
            <tr>
              <th className="px-3 py-2">Moneda</th>
              <th className="px-3 py-2">Precio</th>
              <th className="px-3 py-2">Respaldo</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id} className="border-t">
                <td className="px-3 py-2">{r.currency}</td>
                <td className="px-3 py-2 font-mono">{r.unit_price}</td>
                <td className="px-3 py-2">{r.source_ref}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
