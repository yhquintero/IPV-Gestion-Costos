export function Mark({ className = "h-8 w-8" }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 64 64" role="img" aria-labelledby="markTitle">
      <title id="markTitle">IPV Gestión de Costos</title>
      <rect width="64" height="64" rx="10" fill="#1c4d36" />
      <path d="M12 44h40M18 44V22l14-8 14 8v22" fill="none" stroke="#f3eee4" strokeWidth="2.4" />
      <path d="M32 22v22" stroke="#b8893a" strokeWidth="2.4" />
      <rect x="26" y="30" width="12" height="8" fill="#f3eee4" />
    </svg>
  );
}
