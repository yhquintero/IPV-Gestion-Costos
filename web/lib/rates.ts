import ratesFile from "@/data/exchange-rates-test.json";
import pricesFile from "@/data/prices.json";

export type RateInstrument = keyof typeof ratesFile.instruments;

export const TEST_RATES = ratesFile;
export const PRICE_CATALOG = pricesFile;

export function usdToCup(usd: number): number {
  const raw = Number(ratesFile.instruments.USD);
  return Math.round(usd * raw * 100) / 100;
}

export function formatUsd(n: number): string {
  return new Intl.NumberFormat("es-CU", { style: "currency", currency: "USD" }).format(n);
}

export function formatCup(n: number): string {
  return new Intl.NumberFormat("es-CU", { maximumFractionDigits: 2, minimumFractionDigits: 2 }).format(n) + " CUP";
}

export const RATE_DISCLAIMER =
  "Tasa de referencia, no oficial. DATOS DE PRUEBA (semilla 30/09/2026 12:57). No es tasa oficial de Cuba.";
