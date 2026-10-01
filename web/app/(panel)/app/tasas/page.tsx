import { authedFetch } from "@/lib/backend";
import { RateLabel } from "@/components/RateLabel";

export default async function TasasPage() {
  const rows =
    ((await authedFetch("GET", "/api/v1/rates/current")).json as Array<{
      instrument: string;
      value: string;
      label: string;
    }>) ?? [];
  return (
    <div>
      <h1 className="font-serif text-3xl">Tasas de referencia</h1>
      <RateLabel className="mt-2" />
      <table className="mt-6 min-w-full bg-paper text-left">
        <thead>
          <tr>
            <th className="px-3 py-2">Instrumento</th>
            <th className="px-3 py-2">Valor</th>
            <th className="px-3 py-2">Etiqueta</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.instrument} className="border-t">
              <th className="px-3 py-2" scope="row">
                {r.instrument}
              </th>
              <td className="px-3 py-2 font-mono">{r.value}</td>
              <td className="px-3 py-2 text-sm">{r.label}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
