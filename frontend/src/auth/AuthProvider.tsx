import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { endSession, fetchCurrentUser, updateActiveRole, verifyOtp } from "../lib/api";
import type { CurrentUser, Role } from "../types/api";
import { AuthContext, type AuthContextValue, type AuthStatus } from "./auth-context";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<CurrentUser | null>(null);

  const reload = useCallback(async () => {
    try {
      const current = await fetchCurrentUser();
      setUser(current);
      setStatus("authenticated");
      return current;
    } catch {
      setUser(null);
      setStatus("anonymous");
      return null;
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const signInWithOtp = useCallback(async (phone: string, code: string) => {
    const current = await verifyOtp(phone, code);
    setUser(current);
    setStatus("authenticated");
    return current;
  }, []);

  const signOut = useCallback(async () => {
    try {
      await endSession();
    } finally {
      setUser(null);
      setStatus("anonymous");
    }
  }, []);

  const selectActiveRole = useCallback(async (role: Role) => {
    const current = await updateActiveRole(role);
    setUser(current);
    setStatus("authenticated");
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, reload, signInWithOtp, signOut, selectActiveRole }),
    [status, user, reload, signInWithOtp, signOut, selectActiveRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
