import { createContext, useCallback, useEffect, useState, type ReactNode } from "react";
import * as authApi from "../api/auth";
import { setUnauthorizedHandler } from "../api/client";
import type { Session } from "../types";

interface AuthState {
  session: Session | null;
  /** True until the first /auth/me check finishes. */
  checking: boolean;
  login: (username: string, password: string) => Promise<Session>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    // Any 401 from the gateway (expired, logged out, no cookie) drops the session.
    setUnauthorizedHandler(() => setSession(null));
    authApi
      .me()
      .then(setSession)
      .catch(() => setSession(null))
      .finally(() => setChecking(false));
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const s = await authApi.login(username, password);
    setSession(s);
    return s;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setSession(null);
    }
  }, []);

  return <AuthContext.Provider value={{ session, checking, login, logout }}>{children}</AuthContext.Provider>;
}
