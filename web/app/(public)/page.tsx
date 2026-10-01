import Link from "next/link";

export default function HomePage() {
  return (
    <div>
      <section className="mx-auto grid max-w-6xl gap-10 px-4 py-16 lg:grid-cols-2 lg:items-center">
        <div>
          <p className="text-sm font-medium uppercase tracking-[0.2em] text-gold">Cuba · HTTPS · auditoría</p>
          <h1 className="mt-3 font-serif text-4xl leading-tight text-ink sm:text-5xl">
            Fichas de costo y control IPV, con rastro de quién tocó qué.
          </h1>
          <p className="mt-5 max-w-xl text-lg text-forest">
            Un sitio web profesional y una app Android que comparten el mismo cálculo. Usuarios,
            roles, licencias por persona y tasas de referencia de elTOQUE — nunca como «tasa oficial».
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link href="/prueba" className="rounded-full bg-gold px-5 py-3 font-medium text-ink hover:bg-gold/90">
              Solicitar una prueba
            </Link>
            <Link href="/precios" className="rounded-full border border-forest px-5 py-3 text-forest hover:bg-sand">
              Ver precios en USD
            </Link>
          </div>
        </div>
        <aside className="rounded-2xl border border-ink/10 bg-paper p-6 shadow-card" aria-label="Prioridades del producto">
          <h2 className="font-serif text-2xl">Orden de prioridades</h2>
          <ol className="mt-4 space-y-2 text-forest">
            {["Seguridad", "Trazabilidad", "Corrección de datos", "Mantenibilidad", "Offline", "Rendimiento", "Estética"].map(
              (item, i) => (
                <li key={item} className="flex gap-3">
                  <span className="w-6 font-mono text-gold">{i + 1}</span>
                  {item}
                </li>
              ),
            )}
          </ol>
        </aside>
      </section>
      <section className="mx-auto max-w-6xl px-4 pb-16" aria-labelledby="modulos">
        <h2 id="modulos" className="font-serif text-3xl">
          Lo que cubre el panel
        </h2>
        <ul className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[
            ["Valores IPV", "Precio vigente con respaldo, antes de armar la ficha."],
            ["Ficha de costo", "Siete estados, instantánea de tasa y hash de contenido."],
            ["Control IPV", "Contraste contra versiones, no 1:1 con la ficha."],
            ["Auditoría", "Solo anexado y bloques verificables."],
            ["Licencias", "Por usuario, con Keygen detrás de un puerto."],
            ["Conexión limitada", "Caché de lectura ahora; edición offline por etapas."],
          ].map(([title, copy]) => (
            <li key={title} className="rounded-xl border border-ink/10 bg-paper p-5">
              <h3 className="font-serif text-xl">{title}</h3>
              <p className="mt-2 text-forest">{copy}</p>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
