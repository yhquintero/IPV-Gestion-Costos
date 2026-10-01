import { authedFetch } from "@/lib/backend";

export default async function CatalogoPage() {
  const products = ((await authedFetch("GET", "/api/v1/products")).json as Array<{ id: string; code: string; name: string }>) ?? [];
  const materials =
    ((await authedFetch("GET", "/api/v1/raw-materials")).json as Array<{ id: string; code: string; name: string }>) ?? [];
  return (
    <div>
      <h1 className="font-serif text-3xl">Catálogo</h1>
      <section className="mt-6" aria-labelledby="prod">
        <h2 id="prod" className="font-serif text-xl">
          Productos
        </h2>
        <ul className="mt-2 list-disc pl-5">
          {products.map((p) => (
            <li key={p.id}>
              <span className="font-mono">{p.code}</span> — {p.name}
            </li>
          ))}
        </ul>
      </section>
      <section className="mt-6" aria-labelledby="mat">
        <h2 id="mat" className="font-serif text-xl">
          Insumos
        </h2>
        <ul className="mt-2 list-disc pl-5">
          {materials.map((p) => (
            <li key={p.id}>
              <span className="font-mono">{p.code}</span> — {p.name}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
