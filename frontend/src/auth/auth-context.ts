import { createContext } from "react";
import type { CurrentUser, Role } from "../types/api";

export type AuthStatus = "loading" | "authenticated" | "anonymous";

export interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUser | null;
  reload: () => Promise<CurrentUser | null>;
  signInWithOtp: (phone: string, code: string) => Promise<CurrentUser>;
  signOut: () => Promise<void>;
  selectActiveRole: (role: Role) => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
