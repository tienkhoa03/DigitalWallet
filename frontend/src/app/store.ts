import { configureStore } from '@reduxjs/toolkit';

import { authReducer } from '@/shared/auth/auth.slice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    // Feature slices and RTK Query API slices land in their own feature folder
    // (.claude/rules/frontend_coding.md §2) and register here at scaffold-time.
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
