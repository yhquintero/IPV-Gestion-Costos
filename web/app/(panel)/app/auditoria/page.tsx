import { authedFetch } from "@/lib/backend";

export default async function AuditoriaPage() {
  const verify = (await authedFetch("GET", "/api/v1/audit/verify")).json as { ok?: boolean; events?: number };
  return (
    <div>
      <h1 className="font-serif text-3xl">Auditoría</h1>
      <p className="mt-2 text-forest">Verificación de hashes y bloques (GET /audit/verify).</p>
      <p className="mt-6 rounded-xl bg-paper p-4" data-testid="audit-verify">
        Estado: {verify?.ok ? "íntegra" : "requiere revisión"} · eventos {verify?.events ?? 0}
      </p>
    </div>
  );
}
