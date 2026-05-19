import type { ReactElement } from 'react';
import { Navigate, useLocation } from 'react-router-dom';

import { useAppSelector } from '@/app/hooks';
import type { AuthState } from '@/shared/auth/auth.slice';

/**
 * Functional guards per .claude/rules/frontend_coding.md §5.
 * MUST NOT mix imperative redirects scattered inside page components — wrap routes with
 * <RequireAuth> / <RequireRole> instead. Server-side RBAC remains authoritative
 * (.claude/rules/security.md §3).
 */
export function RequireAuth({ children }: { children: ReactElement }): ReactElement {
  const token = useAppSelector((s) => s.auth.token);
  const location = useLocation();
  if (!token) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return children;
}

export function RequireRole({
  role,
  children,
}: {
  role: AuthState['roles'][number];
  children: ReactElement;
}): ReactElement {
  const auth = useAppSelector((s) => s.auth);
  if (!auth.token) return <Navigate to="/login" replace />;
  if (!auth.roles.includes(role)) return <Navigate to="/forbidden" replace />;
  return children;
}
