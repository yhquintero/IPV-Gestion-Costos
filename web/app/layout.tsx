import type { Metadata } from "next";
import { headers } from "next/headers";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "IPV Gestión de Costos",
    template: "%s · IPV Gestión de Costos",
  },
  description:
    "Fichas de costo, valores IPV y control, con usuarios, auditoría y licencias. Pensado para operar en Cuba.",
  robots: { index: true, follow: true },
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  return (
    <html lang="es-CU">
      <body className="min-h-screen bg-paper font-sans antialiased" data-nonce={nonce}>
        <a className="skip-link" href="#contenido">
          Saltar al contenido
        </a>
        {children}
      </body>
    </html>
  );
}
