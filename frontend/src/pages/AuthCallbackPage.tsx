import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { landingPathFor } from "../auth/redirect";

export function AuthCallbackPage() {
  const { status, user } = useAuth();

  if (status === "loading") {
    return <p className="p-8 text-sm text-slate-500">Signing you in…</p>;
  }

  if (status === "authenticated") {
    return <Navigate to={landingPathFor(user)} replace />;
  }

  return <Navigate to="/login?error=oauth_failed" replace />;
}
