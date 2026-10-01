import { NextRequest, NextResponse } from "next/server";
import { authedFetch } from "@/lib/backend";
import { rejectIfBadCsrf } from "@/lib/csrf";

async function handle(request: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const csrf = rejectIfBadCsrf(request);
  if (csrf) return csrf;
  const { path } = await ctx.params;
  const apiPath = `/api/v1/${path.join("/")}${request.nextUrl.search}`;
  let body: unknown;
  if (request.method !== "GET" && request.method !== "HEAD") {
    const text = await request.text();
    body = text ? JSON.parse(text) : undefined;
  }
  const { status, json } = await authedFetch(request.method, apiPath, body);
  if (status === 204) return new NextResponse(null, { status: 204 });
  return NextResponse.json(json, { status });
}

export const GET = handle;
export const POST = handle;
export const PATCH = handle;
export const PUT = handle;
export const DELETE = handle;
