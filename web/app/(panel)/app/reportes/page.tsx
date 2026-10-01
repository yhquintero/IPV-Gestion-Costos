import { authedFetch } from "@/lib/backend";

export default async function ReportesPage() {
  const rows =
    ((await authedFetch("GET", "/api/v1/reports/cost-sheets")).json as Array<{
      code: string;
      status: string;
      total_cost: number | null;
    }>) ?? [];
  return (
    <div>
      <h1 className="font-serif text-3xl">Reportes</h1>
      <table className="mt-6 min-w-full bg-paper text-left">
        <thead>
          <tr>
            <th className="px-3 py-2">Ficha</th>
            <th className="px-3 py-2">Estado</th>
            <th className="px-3 py-2">Total</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.code} className="border-t">
              <td className="px-3 py-2">{r.code}</td>
              <td className="px-3 py-2">{r.status}</td>
              <td className="px-3 py-2 font-mono">{r.total_cost ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
