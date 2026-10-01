import { NextRequest, NextResponse } from "next/server";
import { ACCESS_COOKIE, CSRF_COOKIE, REFRESH_COOKIE } from "@/lib/session";
import { rejectIfBadCsrf } from "@/lib/csrf";

export async function POST(request: NextRequest) {
  const csrf = rejectIfBadCsrf(request);
  if (csrf) return csrf;
  const res = NextResponse.json({ ok: true });
  res.cookies.set(ACCESS_COOKIE, "", { path: "/", maxAge: 0 });
  res.cookies.set(REFRESH_COOKIE, "", { path: "/", maxAge: 0 });
  res.cookies.set(CSRF_COOKIE, "", { path: "/", maxAge: 0 });
  return res;
}
