import type { Metadata } from "next";

export const metadata: Metadata = { title: "Términos" };

export default function TerminosPage() {
  return (
    <article className="mx-auto max-w-2xl px-4 py-12">
      <h1 className="font-serif text-4xl">Términos de uso</h1>
      <p className="mt-4 text-forest">
        Texto legal pendiente (D-23). Este marcador no constituye un contrato. El software se
        distribuye como propietario hasta que se decida la licencia del código (D-24).
      </p>
    </article>
  );
}
