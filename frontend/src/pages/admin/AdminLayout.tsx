import { NavLink, Outlet } from "react-router-dom";

const LINKS = [
  { to: "/admin/students", label: "Students" },
  { to: "/admin/import", label: "Import CSV" },
  { to: "/admin/academic-years", label: "Academic years" },
  { to: "/admin/classes", label: "Classes" },
  { to: "/admin/calendar", label: "Calendar" },
  { to: "/admin/teachers", label: "Teachers" },
  { to: "/admin/parents", label: "Parents" },
  { to: "/admin/teacher-assignments", label: "Assignments" },
  { to: "/admin/parent-links", label: "Parent links" },
];

const linkClass = ({ isActive }: { isActive: boolean }) =>
  isActive
    ? "rounded-md bg-indigo-50 px-3 py-1.5 text-sm font-medium text-indigo-700"
    : "rounded-md px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-100";

export function AdminLayout() {
  return (
    <div className="space-y-6">
      <nav className="flex flex-wrap gap-1 rounded-lg border border-slate-200 bg-white p-1">
        {LINKS.map((link) => (
          <NavLink key={link.to} to={link.to} className={linkClass}>
            {link.label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </div>
  );
}
