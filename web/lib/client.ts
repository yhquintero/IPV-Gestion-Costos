function readCsrf(): string | undefined {
  if (typeof document === "undefined") return undefined;
  const match = document.cookie.split("; ").find((c) => c.startsWith("ipv_csrf="));
  return match ? decodeURIComponent(match.slice("ipv_csrf=".length)) : undefined;
}

export async function bff<T>(method: string, path: string, body?: unknown): Promise<{ status: number; data: T }> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const csrf = readCsrf();
  if (csrf) headers["x-csrf-token"] = csrf;
  const res = await fetch(`/api/bff${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 204) return { status: 204, data: null as T };
  const data = (await res.json().catch(() => null)) as T;
  return { status: res.status, data };
}

export type Me = {
  id: string;
  organization_id: string;
  email: string;
  display_name: string;
  roles: string[];
  permissions: string[];
};
