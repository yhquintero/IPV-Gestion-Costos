import Link from "next/link";
import { Mark } from "./Mark";

const NAV = [
  { href: "/precios", label: "Precios" },
  { href: "/prueba", label: "Solicitar prueba" },
  { href: "/contacto", label: "Contacto" },
];

export function PublicHeader() {
  return (
    <header className="border-b border-ink/10 bg-paper/90 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
        <Link href="/" className="flex items-center gap-3 text-ink">
          <Mark className="h-9 w-9" />
          <span className="font-serif text-lg leading-tight">
            IPV <span className="hidden sm:inline">Gestión de Costos</span>
          </span>
        </Link>
        <nav aria-label="Principal" className="flex items-center gap-4 text-sm">
          {NAV.map((item) => (
            <Link key={item.href} href={item.href} className="text-forest hover:underline">
              {item.label}
            </Link>
          ))}
          <Link
            href="/login"
            className="rounded-full bg-forest px-4 py-2 font-medium text-sand hover:bg-ink"
          >
            Entrar al panel
          </Link>
        </nav>
      </div>
    </header>
  );
}

export function PublicFooter() {
  return (
    <footer className="mt-16 border-t border-ink/10 bg-sand">
      <div className="mx-auto flex max-w-6xl flex-wrap gap-6 px-4 py-8 text-sm text-forest">
        <p className="max-w-sm">
          Producto unificado de fichas de costo e inventario. Las tasas de elTOQUE son de
          referencia y no oficiales.
        </p>
        <nav aria-label="Legal" className="flex flex-col gap-2">
          <Link href="/terminos" className="hover:underline">
            Términos
          </Link>
          <Link href="/privacidad" className="hover:underline">
            Privacidad
          </Link>
        </nav>
        <p className="ml-auto self-end text-xs">© 2026 IPV Gestión de Costos</p>
      </div>
    </footer>
  );
}
