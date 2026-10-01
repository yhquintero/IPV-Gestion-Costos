import { authedFetch } from "@/lib/backend";
import type { Me } from "@/lib/client";

export default async function InicioPage() {
  const me = (await authedFetch("GET", "/api/v1/me")).json as Me;
  return (
    <div>
      <h1 className="font-serif text-3xl">Inicio</h1>
      <p className="mt-2 text-forest">
        Hola, {me.display_name}. Roles: {me.roles.join(", ") || "—"}.
      </p>
      <ul className="mt-6 grid gap-4 sm:grid-cols-3">
        <li className="rounded-xl bg-paper p-4 shadow-card">
          <h2 className="font-serif text-xl">Fichas</h2>
          <p className="text-sm text-forest">Borrador → vigente, con instantánea de tasa.</p>
        </li>
        <li className="rounded-xl bg-paper p-4 shadow-card">
          <h2 className="font-serif text-xl">Control IPV</h2>
          <p className="text-sm text-forest">Contra versiones, no 1:1.</p>
        </li>
        <li className="rounded-xl bg-paper p-4 shadow-card">
          <h2 className="font-serif text-xl">Auditoría</h2>
          <p className="text-sm text-forest">Verificar bloques firmados.</p>
        </li>
      </ul>
    </div>
  );
}
