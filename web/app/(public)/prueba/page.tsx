import type { Metadata } from "next";

export const metadata: Metadata = { title: "Solicitar prueba" };

export default async function PruebaPage({ searchParams }: { searchParams: Promise<{ ok?: string }> }) {
  const { ok } = await searchParams;
  return (
    <div className="mx-auto max-w-xl px-4 py-12">
      <h1 className="font-serif text-4xl">Solicitar una prueba</h1>
      <p className="mt-3 text-forest">
        Deje sus datos. Un administrador de plataforma revisa la solicitud; no se crea una cuenta
        automáticamente.
      </p>
      {ok ? (
        <p role="status" className="mt-4 rounded-lg bg-paper px-3 py-2" data-testid="prueba-ok">
          Solicitud registrada. No se creó ninguna cuenta.
        </p>
      ) : null}
      <form className="mt-8 space-y-4" method="post" action="/api/bff/trial" noValidate>
        <div>
          <label htmlFor="org" className="block text-sm font-medium">
            Organización
          </label>
          <input id="org" name="org" required className="mt-1 w-full rounded-lg border border-ink/20 bg-paper px-3 py-2" />
        </div>
        <div>
          <label htmlFor="email" className="block text-sm font-medium">
            Correo
          </label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            required
            className="mt-1 w-full rounded-lg border border-ink/20 bg-paper px-3 py-2"
          />
        </div>
        <div>
          <label htmlFor="msg" className="block text-sm font-medium">
            Comentario
          </label>
          <textarea id="msg" name="msg" rows={4} className="mt-1 w-full rounded-lg border border-ink/20 bg-paper px-3 py-2" />
        </div>
        <button type="submit" className="rounded-full bg-forest px-5 py-2 text-sand">
          Enviar solicitud
        </button>
      </form>
    </div>
  );
}
