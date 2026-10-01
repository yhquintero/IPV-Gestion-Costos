import { authedFetch } from "@/lib/backend";

export default async function ContratosPage() {
  const rows =
    ((await authedFetch("GET", "/api/v1/contracts")).json as Array<{
      id: string;
      number: string;
      type: string;
      status: string;
      total_usd: number;
    }>) ?? [];
  const receipts =
    ((await authedFetch("GET", "/api/v1/receipts")).json as Array<{
      id: string;
      number: string;
      amount: number;
      currency: string;
    }>) ?? [];
  return (
    <div>
      <h1 className="font-serif text-3xl">Contratos y recibos</h1>
      <p className="mt-2 text-forest">
        Keygen no factura. El pago confirmado dispara la renovación FROM_EXPIRY (D-12 intacto).
      </p>
      <h2 className="mt-6 font-serif text-xl">Contratos</h2>
      <ul className="mt-3 space-y-2">
        {rows.length === 0 ? <li className="text-forest">Sin contratos en esta organización.</li> : null}
        {rows.map((c) => (
          <li key={c.id} className="rounded-xl bg-paper px-4 py-3 shadow-card">
            {c.number} · {c.type} · {c.status} · {c.total_usd} USD
          </li>
        ))}
      </ul>
      <h2 className="mt-8 font-serif text-xl">Recibos</h2>
      <ul className="mt-3 space-y-2">
        {receipts.length === 0 ? <li className="text-forest">Sin recibos.</li> : null}
        {receipts.map((r) => (
          <li key={r.id} className="rounded-xl bg-paper px-4 py-3 shadow-card">
            {r.number} · {r.amount} {r.currency}
          </li>
        ))}
      </ul>
    </div>
  );
}
