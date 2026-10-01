"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Mark } from "./Mark";
import type { Me } from "@/lib/client";

const LINKS: Array<{ href: string; label: string; perm?: string; platform?: boolean }> = [
  { href: "/app/inicio", label: "Inicio" },
  { href: "/app/catalogo", label: "Catálogo" },
  { href: "/app/valores-ipv", label: "Valores IPV", perm: "costs:view" },
  { href: "/app/fichas", label: "Fichas", perm: "costs:view" },
  { href: "/app/controles", label: "Controles IPV" },
  { href: "/app/inventario", label: "Inventario" },
  { href: "/app/tasas", label: "Tasas" },
  { href: "/app/reportes", label: "Reportes", perm: "costs:view" },
  { href: "/app/auditoria", label: "Auditoría", perm: "ADVANCED_AUDIT" },
  { href: "/app/usuarios", label: "Usuarios", perm: "users:manage" },
  { href: "/app/licencias", label: "Licencias" },
  { href: "/app/configuracion", label: "Configuración" },
  { href: "/plataforma/organizaciones", label: "Organizaciones", platform: true },
  { href: "/plataforma/precios", label: "Catálogo", platform: true },
  { href: "/plataforma/contratos", label: "Contratos", platform: true },
  { href: "/plataforma/licencias", label: "Licencias org.", platform: true },
  { href: "/plataforma/tasas", label: "Proveedor tasas", platform: true },
];

export function PanelShell({ me, children }: { me: Me; children: React.ReactNode }) {
  const path = usePathname();
  const router = useRouter();
  const visible = LINKS.filter((l) => {
    if (l.platform && !me.roles.includes("PLATFORM_ADMIN") && !me.roles.includes("ORG_ADMIN")) return false;
    if (l.perm && !me.permissions.includes(l.perm)) return false;
    return true;
  });

  async function logout() {
    const csrf = document.cookie.split("; ").find((c) => c.startsWith("ipv_csrf="));
    const token = csrf ? decodeURIComponent(csrf.slice("ipv_csrf=".length)) : undefined;
    await fetch("/api/bff/logout", {
      method: "POST",
      headers: token ? { "x-csrf-token": token } : undefined,
    });
    router.push("/login");
    router.refresh();
  }

  return (
    <div className="min-h-screen bg-sand">
      <div className="mx-auto flex max-w-[1400px]">
        <aside className="sticky top-0 flex h-screen w-60 flex-col border-r border-ink/10 bg-ink text-sand">
          <Link href="/app/inicio" className="flex items-center gap-2 px-4 py-4">
            <Mark className="h-8 w-8" />
            <span className="font-serif">IPV</span>
          </Link>
          <nav aria-label="Panel" className="flex-1 overflow-y-auto px-2">
            <ul className="space-y-1">
              {visible.map((item) => {
                const active = path === item.href || path.startsWith(item.href + "/");
                return (
                  <li key={item.href}>
                    <Link
                      href={item.href}
                      className={`block rounded-lg px-3 py-2 text-sm ${active ? "bg-leaf text-sand" : "hover:bg-white/10"}`}
                      aria-current={active ? "page" : undefined}
                    >
                      {item.label}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>
          <div className="border-t border-white/10 p-4 text-xs">
            <p className="font-medium">{me.display_name}</p>
            <p>{me.email}</p>
            <button type="button" onClick={logout} className="mt-2 underline" data-testid="cerrar-sesion">
              Cerrar sesión
            </button>
          </div>
        </aside>
        <div className="min-w-0 flex-1">
          <main id="contenido" className="px-6 py-8">
            {children}
          </main>
        </div>
      </div>
    </div>
  );
}
