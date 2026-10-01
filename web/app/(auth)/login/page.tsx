import { Suspense } from "react";
import { LoginForm } from "./ui";

export default function LoginPage() {
  return (
    <Suspense fallback={<p className="p-8">Cargando sesión…</p>}>
      <LoginForm />
    </Suspense>
  );
}
