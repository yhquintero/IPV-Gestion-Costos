import { authedFetch } from "@/lib/backend";

export default async function PlataformaLicenciasPage() {
  const rows =
    ((await authedFetch("GET", "/api/v1/licenses")).json as Array<{
      id: string;
      user_id: string;
      policy_code: string;
      status: string;
      expires_at: string | null;
      max_devices: number;
    }>) ?? [];
  return (
    <div>
      <h1 className="font-serif text-3xl">Licencias de la organización</h1>
      <p className="mt-2 text-forest">
        Espejo local. La clave de licencia no se persiste (I-18). Vigencia desde la primera
        activación; renovación FROM_EXPIRY.
      </p>
      <ul className="mt-6 space-y-2">
        {rows.length === 0 ? <li className="text-forest">Nadie tiene licencia emitida aún.</li> : null}
        {rows.map((l) => (
          <li key={l.id} className="rounded-xl bg-paper px-4 py-3 shadow-card">
            <span className="font-mono">{l.policy_code}</span> · {l.status} · usuario {l.user_id} ·
            vence {l.expires_at ?? "—"} · {l.max_devices} dispositivos
          </li>
        ))}
      </ul>
    </div>
  );
}
