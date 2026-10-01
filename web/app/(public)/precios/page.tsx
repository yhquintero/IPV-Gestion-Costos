import type { Metadata } from "next";
import { PRICE_CATALOG, RATE_DISCLAIMER, TEST_RATES, formatCup, formatUsd, usdToCup } from "@/lib/rates";
import { RateLabel } from "@/components/RateLabel";

export const metadata: Metadata = { title: "Precios" };

export default function PreciosPage() {
  const usd = Number(TEST_RATES.instruments.USD);
  return (
    <div className="mx-auto max-w-5xl px-4 py-12">
      <h1 className="font-serif text-4xl">Precios en USD</h1>
      <p className="mt-3 max-w-2xl text-forest">
        El cobro se expresa en dólares. El equivalente en CUP se calcula con la semilla de prueba
        y se muestra solo como referencia.
      </p>
      <RateLabel className="mt-3" />
      <p className="mt-1 font-mono text-sm text-forest">
        USD {usd.toFixed(2)} CUP · sello local {TEST_RATES.as_of_local}
      </p>
      <div className="mt-8 overflow-x-auto">
        <table className="min-w-full border-collapse text-left">
          <caption className="sr-only">Planes de licencia en USD y equivalente referencial en CUP</caption>
          <thead className="bg-sand">
            <tr>
              <th scope="col" className="px-4 py-3">
                Plan
              </th>
              <th scope="col" className="px-4 py-3">
                Duración
              </th>
              <th scope="col" className="px-4 py-3">
                Precio USD
              </th>
              <th scope="col" className="px-4 py-3">
                Equivalente CUP
              </th>
            </tr>
          </thead>
          <tbody>
            {PRICE_CATALOG.plans.map((plan) => (
              <tr key={plan.code} className="border-b border-ink/10">
                <th scope="row" className="px-4 py-3 font-medium">
                  {plan.name}
                </th>
                <td className="px-4 py-3">{plan.duration_days} días</td>
                <td className="px-4 py-3 font-mono">{formatUsd(plan.price_usd)}</td>
                <td className="px-4 py-3 font-mono">{formatCup(usdToCup(plan.price_usd))}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="sr-only">{RATE_DISCLAIMER}</p>
    </div>
  );
}
