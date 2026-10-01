import type { Metadata } from "next";

export const metadata: Metadata = { title: "Privacidad" };

export default function PrivacidadPage() {
  return (
    <article className="mx-auto max-w-2xl px-4 py-12">
      <h1 className="font-serif text-4xl">Política de privacidad</h1>
      <p className="mt-4 text-forest">
        Marco de protección de datos y plazos de retención pendientes de dictamen (D-17, D-23).
        El panel no envía analítica por defecto. Las direcciones IP en auditoría son configurables.
      </p>
    </article>
  );
}
