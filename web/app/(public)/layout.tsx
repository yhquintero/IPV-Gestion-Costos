import { PublicFooter, PublicHeader } from "@/components/PublicChrome";

export default function PublicLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="paper-grid min-h-screen">
      <PublicHeader />
      <main id="contenido">{children}</main>
      <PublicFooter />
    </div>
  );
}
