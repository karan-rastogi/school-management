import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Badge,
  Button,
  Card,
  ErrorBanner,
  Field,
  Loading,
  PageHeader,
  Select,
  TextArea,
} from "../components/ui";
import * as attendanceApi from "../lib/attendanceApi";
import type { AttendanceQuery, AttendanceQueryStatus } from "../types/api";

type StatusFilter = "" | AttendanceQueryStatus;

function QueryCard({ query }: { query: AttendanceQuery }) {
  const queryClient = useQueryClient();
  const [response, setResponse] = useState("");

  const resolve = useMutation({
    mutationFn: () => attendanceApi.resolveAttendanceQuery(query.id, response),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["attendance", "queries"] });
    },
  });

  return (
    <li className="space-y-2 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-sm font-medium">{query.studentName}</p>
          <p className="text-xs text-slate-500">
            {query.className ? `${query.className} ${query.section ?? ""} · ` : ""}
            {query.admissionNo} · disputed {query.attendanceDate}
            {query.attendanceStatus ? ` (recorded ${query.attendanceStatus})` : ""}
          </p>
        </div>
        <Badge tone={query.status === "OPEN" ? "amber" : "green"}>{query.status}</Badge>
      </div>

      <p className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
        {query.message}
      </p>

      {query.status === "RESOLVED" ? (
        <p className="text-xs text-slate-500">
          Resolved by {query.respondedByName}: <span className="text-slate-700">{query.response}</span>
        </p>
      ) : (
        <div className="space-y-2">
          <Field label="Response">
            <TextArea
              rows={2}
              value={response}
              onChange={(event) => setResponse(event.target.value)}
              placeholder="Explain what you found or fixed."
            />
          </Field>
          {resolve.isError ? <ErrorBanner error={resolve.error} /> : null}
          <Button
            disabled={resolve.isPending || response.trim() === ""}
            onClick={() => resolve.mutate()}
          >
            {resolve.isPending ? "Resolving…" : "Resolve"}
          </Button>
        </div>
      )}
    </li>
  );
}

export function TeacherQueriesPage() {
  const [status, setStatus] = useState<StatusFilter>("OPEN");

  const queries = useQuery({
    queryKey: ["attendance", "queries", status],
    queryFn: () => attendanceApi.listAttendanceQueries(status === "" ? undefined : status),
  });

  return (
    <section className="mx-auto max-w-3xl space-y-6">
      <PageHeader
        title="Attendance queries"
        description="Parents can dispute a date. Queries for the classes you are the class teacher of appear here."
      />

      <Card className="p-4">
        <Field label="Status">
          <Select value={status} onChange={(event) => setStatus(event.target.value as StatusFilter)}>
            <option value="OPEN">Open</option>
            <option value="RESOLVED">Resolved</option>
            <option value="">All</option>
          </Select>
        </Field>
      </Card>

      {queries.isPending ? <Loading label="Loading queries…" /> : null}
      {queries.isError ? <ErrorBanner error={queries.error} /> : null}

      {queries.data ? (
        <Card className="p-4">
          {queries.data.length === 0 ? (
            <p className="text-sm text-slate-600">No queries here.</p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {queries.data.map((query) => (
                <QueryCard key={query.id} query={query} />
              ))}
            </ul>
          )}
        </Card>
      ) : null}
    </section>
  );
}
