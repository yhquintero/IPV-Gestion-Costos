import { authedFetch } from "@/lib/backend";

export default async function OrganizacionesPage() {
  const rows =
    ((await authedFetch("GET", "/api/v1/organizations")).json as Array<{
      id: string;
      name: string;
      status: string;
    }>) ?? [];
  return (
    <div>
      <h1 className="font-serif text-3xl">Organizaciones</h1>
      <p className="mt-2 text-forest">Alcance de plataforma. Catálogo, contratos y licencias están en el menú.</p>
      <ul className="mt-6 space-y-2">
        {rows.map((o) => (
          <li key={o.id} className="rounded-xl bg-paper px-4 py-3 shadow-card">
            <span className="font-medium">{o.name}</span> · {o.status}
          </li>
        ))}
      </ul>
    </div>
  );
}
