import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      // Design tokens live here per .claude/rules/frontend_coding.md §6.
      // MUST NOT inline arbitrary hex values in JSX — extend this object instead.
      colors: {
        brand: {
          DEFAULT: '#0f172a',
          accent: '#2563eb',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
};

export default config;
