import { useQuery } from "@tanstack/react-query";
import { fetchAdminUsers, toApiError } from "../lib/api";

export function AdminPage() {
  const users = useQuery({
    queryKey: ["admin", "users"],
    queryFn: fetchAdminUsers,
  });

  return (
    <section className="space-y-6">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold">Users</h1>
        <p className="text-sm text-slate-600">
          Only administrators can open this page, and the server enforces that too.
        </p>
      </header>

      {users.isPending ? <p className="text-sm text-slate-600">Loading users…</p> : null}

      {users.isError ? <p className="text-sm text-red-600">{toApiError(users.error).message}</p> : null}

      {users.data ? (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-2">Name</th>
                <th className="px-4 py-2">Roles</th>
                <th className="px-4 py-2">Status</th>
              </tr>
            </thead>
            <tbody>
              {users.data.map((user) => (
                <tr key={user.id} className="border-t border-slate-100">
                  <td className="px-4 py-2 font-medium">{user.name}</td>
                  <td className="px-4 py-2 text-slate-600">{user.roles.join(", ")}</td>
                  <td className="px-4 py-2">
                    <span className={user.active ? "text-emerald-700" : "text-slate-400"}>
                      {user.active ? "Active" : "Inactive"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}
