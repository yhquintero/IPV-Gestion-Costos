import { authedFetch } from "@/lib/backend";

export default async function UsuariosPage() {
  const { status, json } = await authedFetch("GET", "/api/v1/users");
  const rows =
    status === 200
      ? (json as Array<{ id: string; email: string; display_name: string; roles: string[] }>)
      : [];
  return (
    <div>
      <h1 className="font-serif text-3xl">Usuarios y roles</h1>
      <p className="mt-2 text-forest">Cuentas sintéticas de demostración. El alta con alcance ORG/COMPANY/BRANCH usa la API de identidad.</p>
      {status === 403 ? (
        <p role="alert">No tiene permiso para gestionar usuarios.</p>
      ) : (
        <table className="mt-6 min-w-full bg-paper text-left">
          <thead>
            <tr>
              <th className="px-3 py-2">Nombre</th>
              <th className="px-3 py-2">Correo</th>
              <th className="px-3 py-2">Roles</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((u) => (
              <tr key={u.id} className="border-t">
                <th className="px-3 py-2 font-medium" scope="row">
                  {u.display_name}
                </th>
                <td className="px-3 py-2">{u.email}</td>
                <td className="px-3 py-2">{u.roles.join(", ")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
