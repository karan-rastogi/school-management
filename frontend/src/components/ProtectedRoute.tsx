import type { ReactNode } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import type { Role } from "../types/api";

interface ProtectedRouteProps {
  requiredRole?: Role | Role[];
  children?: ReactNode;
}

export function ProtectedRoute({ requiredRole, children }: ProtectedRouteProps) {
  const { status, user } = useAuth();
  const location = useLocation();

  if (status === "loading") {
    return <p className="p-8 text-sm text-slate-500">Loading…</p>;
  }

  if (status === "anonymous" || !user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  const required = requiredRole == null ? [] : Array.isArray(requiredRole) ? requiredRole : [requiredRole];

  if (required.length > 0 && !required.some((role) => user.roles.includes(role))) {
    return (
      <section className="space-y-2 rounded-lg border border-slate-200 bg-white p-6">
        <h1 className="text-xl font-semibold">Not available for your role</h1>
        <p className="text-sm text-slate-600">
          This page needs the {required.join(" or ")} role, which your account does not hold.
        </p>
      </section>
    );
  }

  return <>{children ?? <Outlet />}</>;
}
