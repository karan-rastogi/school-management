import { NavLink, Outlet } from "react-router-dom";

export function AppLayout() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <nav className="mx-auto flex max-w-5xl items-center gap-6 px-4 py-4">
          <span className="text-base font-semibold">School Management</span>
          <NavLink
            to="/"
            end
            className={({ isActive }) =>
              isActive ? "text-sm font-medium text-indigo-600" : "text-sm font-medium text-slate-500 hover:text-slate-900"
            }
          >
            Home
          </NavLink>
        </nav>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
