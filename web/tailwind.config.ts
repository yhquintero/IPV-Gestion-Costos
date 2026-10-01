import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#13241c",
        forest: "#1c4d36",
        leaf: "#2f7a52",
        gold: "#b8893a",
        sand: "#f3eee4",
        paper: "#fbfaf6",
        brick: "#8c2f2f",
      },
      fontFamily: {
        sans: ["Source Sans 3", "Segoe UI", "system-ui", "sans-serif"],
        serif: ["Iowan Old Style", "Palatino Linotype", "Palatino", "Georgia", "serif"],
        mono: ["ui-monospace", "Cascadia Code", "Source Code Pro", "monospace"],
      },
      boxShadow: {
        card: "0 1px 0 rgba(19,36,28,0.06), 0 12px 32px rgba(19,36,28,0.08)",
      },
    },
  },
  plugins: [],
};

export default config;
