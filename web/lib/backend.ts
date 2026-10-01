import { getAccessToken } from "./session";
import { mockHandle } from "./mock-backend";

export function isMockBackend(): boolean {
  const env = process.env.APP_ENV ?? "dev";
  const url = process.env.API_URL ?? "";
  if (env === "prod" && !url) {
    throw new Error("API_URL is required in production (BFF refuses mock backend)");
  }
  return !url || process.env.MOCK_API === "1";
}

export async function backendFetch(
  method: string,
  path: string,
  body?: unknown,
  token?: string,
  extraHeaders?: Record<string, string>,
): Promise<{ status: number; json: unknown }> {
  if (isMockBackend()) {
    return mockHandle(method, path, body, token);
  }
  const api = process.env.API_URL!.replace(/\/$/, "");
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...(extraHeaders ?? {}),
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const res = await fetch(`${api}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
  });
  const text = await res.text();
  let json: unknown = null;
  if (text) {
    try {
      json = JSON.parse(text);
    } catch {
      json = { detail: text };
    }
  }
  return { status: res.status, json };
}

export async function authedFetch(method: string, path: string, body?: unknown) {
  const token = await getAccessToken();
  if (!token) return { status: 401, json: { code: "unauthenticated", detail: "no session" } };
  return backendFetch(method, path, body, token);
}
