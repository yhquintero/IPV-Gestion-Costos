import { authedFetch } from "@/lib/backend";

type LicenseMe = {
  id?: string;
  policy_code?: string;
  status: string;
  blocks_access?: boolean;
  expires_at?: string | null;
  remaining_days?: number | null;
  entitlements?: string[];
  max_devices?: number;
};

export default async function LicenciasPage() {
  const me = ((await authedFetch("GET", "/api/v1/licenses/me")).json as LicenseMe) ?? {
    status: "NOT_ACTIVATED",
  };
  return (
    <div>
      <h1 className="font-serif text-3xl">Estado de licencia</h1>
      <p className="mt-2 text-forest">
        El estado se recalcula (tabla 7.6). No hay bandera «premium» en el navegador. KEYGEN Cloud
        está bloqueado hasta D-05; este corte usa el proveedor FAKE.
      </p>
      <dl className="mt-6 grid max-w-xl gap-3 rounded-xl bg-paper p-4 shadow-card">
        <div>
          <dt className="text-sm text-forest">Estado</dt>
          <dd className="font-medium">{me.status}</dd>
        </div>
        <div>
          <dt className="text-sm text-forest">Política</dt>
          <dd>{me.policy_code ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-sm text-forest">Vence</dt>
          <dd className="font-mono">{me.expires_at ?? "sin activar"}</dd>
        </div>
        <div>
          <dt className="text-sm text-forest">Derechos</dt>
          <dd>{(me.entitlements ?? []).join(", ") || "ninguno"}</dd>
        </div>
      </dl>
    </div>
  );
}
