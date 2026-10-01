import { NextResponse } from "next/server";
import { backendFetch } from "@/lib/backend";
import { ACCESS_COOKIE, CSRF_COOKIE, REFRESH_COOKIE, sessionOptions } from "@/lib/session";

export async function POST(request: Request) {
  const body = await request.json().catch(() => ({}));
  const { status, json } = await backendFetch("POST", "/api/v1/auth/login", body);
  if (status !== 200 || !json || typeof json !== "object") {
    return NextResponse.json(json ?? { code: "invalid_credentials" }, { status });
  }
  const payload = json as {
    access_token?: string;
    refresh_token?: string;
    mfa_required?: boolean;
    user?: unknown;
  };
  if (payload.mfa_required) {
    return NextResponse.json(payload, { status: 200 });
  }
  const res = NextResponse.json({ user: payload.user, mfa_required: false });
  if (payload.access_token) {
    res.cookies.set(ACCESS_COOKIE, payload.access_token, sessionOptions(900));
  }
  if (payload.refresh_token) {
    res.cookies.set(REFRESH_COOKIE, payload.refresh_token, sessionOptions(60 * 60 * 24 * 30));
  }
  const csrf = crypto.randomUUID();
  res.cookies.set(CSRF_COOKIE, csrf, { ...sessionOptions(60 * 60 * 24 * 30), httpOnly: false });
  return res;
}
