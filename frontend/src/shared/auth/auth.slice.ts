import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

/**
 * Per .claude/rules/security.md §6: the JWT is held in memory for the lifetime of the tab.
 * MUST NOT persist the token itself to localStorage.
 */
export interface AuthState {
  token: string | null;
  accountId: string | null;
  roles: Array<'USER' | 'ADMIN' | 'FRAUD_ANALYST'>;
}

const initialState: AuthState = {
  token: null,
  accountId: null,
  roles: [],
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    sessionEstablished(
      state,
      action: PayloadAction<{ token: string; accountId: string; roles: AuthState['roles'] }>,
    ) {
      state.token = action.payload.token;
      state.accountId = action.payload.accountId;
      state.roles = action.payload.roles;
    },
    sessionCleared(state) {
      state.token = null;
      state.accountId = null;
      state.roles = [];
    },
  },
});

export const { sessionEstablished, sessionCleared } = authSlice.actions;
export const authReducer = authSlice.reducer;
