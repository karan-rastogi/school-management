import { createContext } from "react";
import type { CurrentUser, Role } from "../types/api";

export type AuthStatus = "loading" | "authenticated" | "anonymous";

export interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUser | null;
  reload: () => Promise<CurrentUser | null>;
  signInWithPassword: (email: string, password: string) => Promise<CurrentUser>;
  signInWithOtp: (phone: string, code: string) => Promise<CurrentUser>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<CurrentUser>;
  signOut: () => Promise<void>;
  selectActiveRole: (role: Role) => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
