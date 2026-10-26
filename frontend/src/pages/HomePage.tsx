import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../auth/useAuth";
import { fetchHealth, toApiError } from "../lib/api";

export function HomePage() {
  const { user } = useAuth();
  const health = useQuery({
    queryKey: ["health"],
    queryFn: fetchHealth,
  });

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold">Welcome, {user?.name}</h1>
        <p className="text-slate-600">
          You are signed in as {user?.activeRole}
          {user && user.roles.length > 1 ? ` (your account also holds ${user.roles.join(", ")})` : ""}.
        </p>
      </header>

      <div className="rounded-lg border border-slate-200 bg-white p-4">
        <h2 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Backend status</h2>

        {health.isPending ? <p className="mt-2 text-sm text-slate-600">Checking backend…</p> : null}

        {health.isError ? (
          <div className="mt-2 space-y-2">
            <p className="text-sm text-red-600">{toApiError(health.error).message}</p>
            <button
              type="button"
              onClick={() => void health.refetch()}
              className="rounded-md border border-slate-300 px-3 py-1 text-sm font-medium hover:bg-slate-100"
            >
              Retry
            </button>
          </div>
        ) : null}

        {health.data ? (
          <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
            <dt className="text-slate-500">Status</dt>
            <dd className="font-medium">{health.data.status}</dd>
            <dt className="text-slate-500">Application</dt>
            <dd className="font-medium">{health.data.application}</dd>
            <dt className="text-slate-500">Time zone</dt>
            <dd className="font-medium">{health.data.timeZone}</dd>
            <dt className="text-slate-500">Server time</dt>
            <dd className="font-medium">{health.data.timestamp}</dd>
          </dl>
        ) : null}
      </div>
    </section>
  );
}
