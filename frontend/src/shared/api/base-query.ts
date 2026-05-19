import { fetchBaseQuery, type BaseQueryFn, type FetchArgs, type FetchBaseQueryError } from '@reduxjs/toolkit/query/react';

import type { RootState } from '@/app/store';
import { sessionCleared } from '@/shared/auth/auth.slice';


/**
 * Per .claude/rules/frontend_coding.md §3 — RTK Query baseQuery is the single network entrypoint.
 *  - Injects `Authorization: Bearer <token>` from the in-memory auth slice (.claude/rules/security.md §6).
 *  - On 401, dispatches sessionCleared and redirects to /login (MUST NOT silently retry).
 *  - Wire-shape error envelope is `{ error_key, message }` per docs/api/README.md.
 */
const rawBaseQuery = fetchBaseQuery({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api',
  prepareHeaders: (headers, { getState }) => {
    const token = (getState() as RootState).auth.token;
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  },
});

export const baseQuery: BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError> = async (
  args,
  api,
  extraOptions,
) => {
  const result = await rawBaseQuery(args, api, extraOptions);
  if (result.error && result.error.status === 401) {
    api.dispatch(sessionCleared());
    if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
      window.location.assign('/login');
    }
  }
  return result;
};
