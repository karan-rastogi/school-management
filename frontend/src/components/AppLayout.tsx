import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { RoleSwitcher } from "./RoleSwitcher";

const linkClass = ({ isActive }: { isActive: boolean }) =>
  isActive ? "text-sm font-medium text-indigo-600" : "text-sm font-medium text-slate-500 hover:text-slate-900";

export function AppLayout() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  async function handleSignOut() {
    await signOut();
    navigate("/login", { replace: true });
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <nav className="mx-auto flex max-w-5xl flex-wrap items-center gap-6 px-4 py-4">
          <span className="text-base font-semibold">School Management</span>
          <NavLink to="/" end className={linkClass}>
            Home
          </NavLink>
          {user?.roles.some((role) => role === "TEACHER" || role === "ADMIN") ? (
            <NavLink to="/attendance" className={linkClass}>
              Attendance
            </NavLink>
          ) : null}
          {user?.roles.some((role) => role === "TEACHER" || role === "ADMIN") ? (
            <NavLink to="/queries" className={linkClass}>
              Queries
            </NavLink>
          ) : null}
          {user?.roles.some((role) => role === "TEACHER" || role === "ADMIN") ? (
            <NavLink to="/notices" className={linkClass}>
              Notices
            </NavLink>
          ) : null}
          {user?.roles.includes("PARENT") ? (
            <NavLink to="/parent" className={linkClass}>
              My children
            </NavLink>
          ) : null}
          {user?.roles.includes("PARENT") ? (
            <NavLink to="/parent/notices" className={linkClass}>
              Notices
            </NavLink>
          ) : null}
          {user?.roles.includes("ADMIN") ? (
            <NavLink to="/admin" className={linkClass}>
              Admin
            </NavLink>
          ) : null}

          <div className="ml-auto flex items-center gap-4">
            <RoleSwitcher />
            {user ? <span className="text-sm text-slate-500">{user.name}</span> : null}
            <button
              type="button"
              onClick={() => void handleSignOut()}
              className="rounded-md border border-slate-300 px-3 py-1 text-sm font-medium hover:bg-slate-100"
            >
              Sign out
            </button>
          </div>
        </nav>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
