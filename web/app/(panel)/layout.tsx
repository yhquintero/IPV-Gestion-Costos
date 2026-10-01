import { redirect } from "next/navigation";
import { authedFetch } from "@/lib/backend";
import { PanelShell } from "@/components/PanelShell";
import type { Me } from "@/lib/client";

export const dynamic = "force-dynamic";

export default async function PanelLayout({ children }: { children: React.ReactNode }) {
  const { status, json } = await authedFetch("GET", "/api/v1/me");
  if (status !== 200) redirect("/login");
  return <PanelShell me={json as Me}>{children}</PanelShell>;
}
