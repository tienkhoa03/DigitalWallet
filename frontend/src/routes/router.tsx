import { createBrowserRouter } from 'react-router-dom';

import { App } from '@/App';

/**
 * Route table per .claude/rules/frontend_coding.md §5. Feature pages register their own
 * subroutes by importing into this file and adding a child entry. Guards (<RequireAuth>,
 * <RequireRole>) wrap protected branches.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      // Public
      // { path: 'login', element: <LoginPage /> },
      // { path: 'signup', element: <SignupPage /> },

      // USER-scope (RequireAuth)
      // { path: 'wallets/*', element: <RequireAuth>...</RequireAuth> },

      // ADMIN-scope (RequireRole "ADMIN")
      // { path: 'admin/dashboard', element: <RequireRole role="ADMIN">...</RequireRole> },

      // FRAUD_ANALYST-scope
      // { path: 'admin/fraud/*', element: <RequireRole role="FRAUD_ANALYST">...</RequireRole> },
    ],
  },
]);
