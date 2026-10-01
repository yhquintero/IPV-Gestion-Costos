import type { Metadata } from "next";

export const metadata: Metadata = { title: "Contacto" };

export default function ContactoPage() {
  return (
    <div className="mx-auto max-w-xl px-4 py-12">
      <h1 className="font-serif text-4xl">Contacto</h1>
      <p className="mt-3 text-forest">
        El dominio, la entidad legal y el correo de soporte están pendientes de definición (D-23).
        Mientras tanto, use este formulario de demostración.
      </p>
      <form className="mt-8 space-y-4" method="post" action="/api/bff/trial">
        <div>
          <label htmlFor="nombre" className="block text-sm font-medium">
            Nombre
          </label>
          <input id="nombre" name="nombre" required className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2" />
        </div>
        <div>
          <label htmlFor="email" className="block text-sm font-medium">
            Correo
          </label>
          <input id="email" name="email" type="email" required className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2" />
        </div>
        <button type="submit" className="rounded-full bg-forest px-5 py-2 text-sand">
          Enviar
        </button>
      </form>
    </div>
  );
}
