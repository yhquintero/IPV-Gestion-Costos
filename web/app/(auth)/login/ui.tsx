"use client";

import { FormEvent, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Mark } from "@/components/Mark";
import Link from "next/link";

export function LoginForm() {
  const nextParam = useSearchParams().get("next") ?? "/app/inicio";
  const next = nextParam.startsWith("/") && !nextParam.startsWith("//") ? nextParam : "/app/inicio";
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setPending(true);
    const form = new FormData(event.currentTarget);
    const res = await fetch("/api/bff/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: String(form.get("email") ?? ""),
        password: String(form.get("password") ?? ""),
      }),
    });
    setPending(false);
    if (!res.ok) {
      setError("No se pudo iniciar sesión. Verifique el correo y la contraseña.");
      return;
    }
    // Carga completa para que el middleware vea la cookie HttpOnly (router.push pierde la carrera).
    window.location.assign(next);
  }

  return (
    <div className="paper-grid flex min-h-screen items-center justify-center px-4">
      <main id="contenido" className="w-full max-w-md rounded-2xl border border-ink/10 bg-paper p-8 shadow-card">
        <Link href="/" className="flex items-center gap-3 text-ink">
          <Mark />
          <span className="font-serif text-xl">IPV Gestión de Costos</span>
        </Link>
        <h1 className="mt-6 font-serif text-3xl">Entrar al panel</h1>
        <p className="mt-2 text-sm text-forest">
          La sesión vive en una cookie HttpOnly. El navegador no recibe el token JWT en JavaScript.
        </p>
        <form className="mt-6 space-y-4" onSubmit={onSubmit}>
          <div>
            <label htmlFor="email" className="block text-sm font-medium">
              Correo
            </label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="username"
              required
              defaultValue="costeador@alpha.test"
              className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2"
            />
          </div>
          <div>
            <label htmlFor="password" className="block text-sm font-medium">
              Contraseña
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              defaultValue="Seed-Passw0rd!"
              className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2"
            />
          </div>
          {error ? (
            <p role="alert" className="text-sm text-brick">
              {error}
            </p>
          ) : null}
          <button
            type="submit"
            disabled={pending}
            data-testid="login-submit"
            className="w-full rounded-full bg-forest py-2 text-sand disabled:opacity-60"
          >
            {pending ? "Entrando…" : "Entrar"}
          </button>
        </form>
        <p className="mt-4 text-xs text-forest">
          Demostración: cuentas sintéticas <code>@alpha.test</code> / <code>Seed-Passw0rd!</code>. No son
          credenciales reales.
        </p>
      </main>
    </div>
  );
}
