import { NextRequest, NextResponse } from "next/server";
import { CSRF_COOKIE } from "./session";

export function rejectIfBadCsrf(request: NextRequest): NextResponse | null {
  if (request.method === "GET" || request.method === "HEAD" || request.method === "OPTIONS") {
    return null;
  }
  const cookie = request.cookies.get(CSRF_COOKIE)?.value;
  const header = request.headers.get("x-csrf-token");
  if (!cookie || !header || cookie !== header) {
    return NextResponse.json(
      { type: "about:blank", status: 403, code: "csrf", detail: "CSRF token mismatch" },
      { status: 403 },
    );
  }
  return null;
}
