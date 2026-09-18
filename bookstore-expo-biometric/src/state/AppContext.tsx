import React, { createContext, useContext, useEffect, useReducer, useRef } from 'react';
import type { TokenSet } from '../auth/authManager';
import { hasStoredToken, isBiometricEnabled, lastUsername } from '../vault/biometricVault';
import { initialState, reducer } from './reducer';
import type { Action, UiState } from './types';

interface AppContextValue {
  state: UiState;
  dispatch: React.Dispatch<Action>;
  /** Latest token set — kept in a ref (not reducer state) since it's a secret, not UI state. */
  tokensRef: React.MutableRefObject<TokenSet | null>;
}

const AppContext = createContext<AppContextValue | null>(null);

/** Analog of AppViewModel.usernameWithUsableVault() — only offer "unlock with fingerprint"
 * if there's a saved username with BOTH biometric enabled AND a token actually stored. */
async function usernameWithUsableVault(): Promise<string | null> {
  const username = await lastUsername();
  if (!username) return null;
  const [enabled, hasToken] = await Promise.all([isBiometricEnabled(username), hasStoredToken(username)]);
  return enabled && hasToken ? username : null;
}

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);
  const tokensRef = useRef<TokenSet | null>(null);

  useEffect(() => {
    usernameWithUsableVault().then((savedUsername) => {
      dispatch({ type: 'loggedOut', savedUsername });
    });
  }, []);

  return (
    <AppContext.Provider value={{ state, dispatch, tokensRef }}>{children}</AppContext.Provider>
  );
}

export function useApp(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used within an AppProvider');
  return ctx;
}
