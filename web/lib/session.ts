import { cookies } from "next/headers";

export const ACCESS_COOKIE = "ipv_access";
export const REFRESH_COOKIE = "ipv_refresh";
export const CSRF_COOKIE = "ipv_csrf";

export function cookieSecure(): boolean {
  return process.env.COOKIE_SECURE === "true" || process.env.APP_ENV === "prod";
}

export function sessionOptions(maxAge: number) {
  return {
    httpOnly: true,
    secure: cookieSecure(),
    sameSite: "lax" as const,
    path: "/",
    maxAge,
  };
}

export async function getAccessToken(): Promise<string | undefined> {
  const jar = await cookies();
  return jar.get(ACCESS_COOKIE)?.value;
}
