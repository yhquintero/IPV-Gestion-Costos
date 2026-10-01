// Carga contra staging. No se ejecuta en este entorno ni contra producción.
// k6 run -e BASE=https://staging.ejemplo.tld/api/v1 tools/k6/auth-and-health.js
import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 5,
  duration: "30s",
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<800"],
  },
};

const BASE = __ENV.BASE || "http://127.0.0.1:8080/api/v1";

export default function () {
  const health = http.get(`${BASE.replace(/\/api\/v1$/, "")}/actuator/health`);
  check(health, { "health 200": (r) => r.status === 200 });

  const login = http.post(
    `${BASE}/auth/login`,
    JSON.stringify({ email: "nobody@example.test", password: "wrong" }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(login, { "login not 500": (r) => r.status !== 500 && r.status !== 200 });
  sleep(1);
}
