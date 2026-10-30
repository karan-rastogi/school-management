import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  changePassword as changePasswordRequest,
  endSession,
  fetchCurrentUser,
  passwordLogin,
  updateActiveRole,
  verifyOtp,
} from "../lib/api";
import type { CurrentUser, Role } from "../types/api";
import { AuthContext, type AuthContextValue, type AuthStatus } from "./auth-context";

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
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

  const signInWithPassword = useCallback(async (email: string, password: string) => {
    const current = await passwordLogin(email, password);
    setUser(current);
    setStatus("authenticated");
    return current;
  }, []);

  const signInWithOtp = useCallback(async (phone: string, code: string) => {
    const current = await verifyOtp(phone, code);
    setUser(current);
    setStatus("authenticated");
    return current;
  }, []);

  const changePassword = useCallback(async (currentPassword: string, newPassword: string) => {
    const current = await changePasswordRequest(currentPassword, newPassword);
    setUser(current);
    setStatus("authenticated");
    return current;
  }, []);

  const signOut = useCallback(async () => {
    try {
      await endSession();
    } finally {
      // Clear client state and cached protected data so nothing survives log-out, including when
      // the user navigates back to a protected page.
      queryClient.clear();
      setUser(null);
      setStatus("anonymous");
    }
  }, [queryClient]);

  const selectActiveRole = useCallback(async (role: Role) => {
    const current = await updateActiveRole(role);
    setUser(current);
    setStatus("authenticated");
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      reload,
      signInWithPassword,
      signInWithOtp,
      changePassword,
      signOut,
      selectActiveRole,
    }),
    [status, user, reload, signInWithPassword, signInWithOtp, changePassword, signOut, selectActiveRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
