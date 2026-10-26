import { useState } from "react";
import { useAuth } from "../auth/useAuth";
import { toApiError } from "../lib/api";
import type { Role } from "../types/api";

export function RoleSwitcher() {
  const { user, selectActiveRole } = useAuth();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!user || user.roles.length < 2) {
    return null;
  }

  async function handleChange(role: Role) {
    setPending(true);
    setError(null);
    try {
      await selectActiveRole(role);
    } catch (caught) {
      setError(toApiError(caught).message);
    } finally {
      setPending(false);
    }
  }

  return (
    <span className="flex items-center gap-2 text-sm">
      <span className="text-slate-500">Acting as</span>
      <select
        value={user.activeRole}
        disabled={pending}
        onChange={(event) => void handleChange(event.target.value as Role)}
        className="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm disabled:opacity-60"
      >
        {user.roles.map((role) => (
          <option key={role} value={role}>
            {role}
          </option>
        ))}
      </select>
      {error ? <span className="text-red-600">{error}</span> : null}
    </span>
  );
}
