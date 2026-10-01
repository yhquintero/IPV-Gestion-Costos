import { NextRequest, NextResponse } from "next/server";

export async function POST(request: NextRequest) {
  const url = new URL("/prueba?ok=1", request.url);
  return NextResponse.redirect(url, 303);
}
