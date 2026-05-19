import { Outlet } from 'react-router-dom';

/**
 * Routed shell. Top-level <ErrorBoundary> wraps this in main.tsx per
 * .claude/rules/frontend_coding.md §14.
 */
export function App() {
  return (
    <div className="min-h-screen bg-white text-brand">
      <header className="border-b border-slate-200 px-6 py-4">
        <h1 className="text-lg font-semibold">DigitalWallet</h1>
      </header>
      <main className="px-6 py-8">
        <Outlet />
      </main>
    </div>
  );
}
