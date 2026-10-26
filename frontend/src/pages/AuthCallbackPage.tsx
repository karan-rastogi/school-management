import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";

export function AuthCallbackPage() {
  const { status } = useAuth();

  if (status === "loading") {
    return <p className="p-8 text-sm text-slate-500">Signing you in…</p>;
  }

  if (status === "authenticated") {
    return <Navigate to="/" replace />;
  }

  return <Navigate to="/login?error=oauth_failed" replace />;
}
