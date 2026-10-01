import { RATE_DISCLAIMER } from "@/lib/rates";

export function RateLabel({ className = "" }: { className?: string }) {
  return (
    <p className={`text-sm text-forest/80 ${className}`} role="note">
      {RATE_DISCLAIMER}
    </p>
  );
}
