import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
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
  TextInput,
} from "../components/ui";
import * as parentApi from "../lib/parentApi";
import type { AttendanceStatus, ChildAttendanceDay } from "../types/api";

const WEEKDAY_LABELS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

const STATUS_LABELS: Record<AttendanceStatus, string> = {
  PRESENT: "Present",
  ABSENT: "Absent",
  LATE: "Late",
  HALF_DAY: "Half day",
  EXCUSED: "Excused",
};

const LEGEND: Array<{ className: string; label: string }> = [
  { className: "bg-emerald-50 border-emerald-200", label: "Present" },
  { className: "bg-amber-50 border-amber-200", label: "Late / half day" },
  { className: "bg-red-50 border-red-200", label: "Absent" },
  { className: "bg-indigo-50 border-indigo-200", label: "Excused" },
  { className: "bg-slate-100 border-slate-200", label: "Holiday / weekend" },
  { className: "border-dashed border-slate-300 bg-white", label: "Not taken" },
];

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function todayIso(): string {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

function shiftMonth(month: string, delta: number): string {
  const [year, monthNumber] = month.split("-").map(Number);
  const date = new Date(Date.UTC(year, monthNumber - 1 + delta, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
}

function monthLabel(month: string): string {
  const [year, monthNumber] = month.split("-").map(Number);
  return new Date(Date.UTC(year, monthNumber - 1, 1)).toLocaleDateString("en-GB", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

function dayClasses(day: ChildAttendanceDay): string {
  const base = "flex h-16 flex-col items-start gap-0.5 rounded-md border p-1.5 text-left";
  if (!day.working) {
    return `${base} border-slate-200 bg-slate-100 text-slate-400`;
  }
  switch (day.status) {
    case "PRESENT":
      return `${base} border-emerald-200 bg-emerald-50 text-emerald-700`;
    case "LATE":
    case "HALF_DAY":
      return `${base} border-amber-200 bg-amber-50 text-amber-700`;
    case "ABSENT":
      return `${base} border-red-200 bg-red-50 text-red-700`;
    case "EXCUSED":
      return `${base} border-indigo-200 bg-indigo-50 text-indigo-700`;
    default:
      return day.future
        ? `${base} border-slate-200 bg-white text-slate-300`
        : `${base} border-dashed border-slate-300 bg-white text-slate-400`;
  }
}

export function ParentDashboardPage() {
  const queryClient = useQueryClient();
  const [studentId, setStudentId] = useState<number | "">("");
  const [month, setMonth] = useState(currentMonth);
  const [queryDate, setQueryDate] = useState("");
  const [queryMessage, setQueryMessage] = useState("");
  const [confirmation, setConfirmation] = useState<string | null>(null);

  const children = useQuery({ queryKey: ["parent", "children"], queryFn: parentApi.listChildren });

  useEffect(() => {
    if (studentId === "" && children.data && children.data.length > 0) {
      setStudentId(children.data[0].studentId);
    }
  }, [children.data, studentId]);

  const attendance = useQuery({
    queryKey: ["parent", "attendance", studentId, month],
    queryFn: () => parentApi.childAttendance(Number(studentId), month),
    enabled: studentId !== "",
  });

  const raise = useMutation({
    mutationFn: () =>
      parentApi.raiseAttendanceQuery(Number(studentId), { date: queryDate, message: queryMessage }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["parent", "attendance", studentId] });
      setQueryDate("");
      setQueryMessage("");
      setConfirmation("Your query was sent to the class teacher.");
    },
  });

  const selected = children.data?.find((child) => child.studentId === studentId);
  const data = attendance.data;
  const leadingBlanks = data && data.days.length > 0
    ? new Date(`${data.days[0].date}T00:00:00Z`).getUTCDay()
    : 0;

  return (
    <section className="space-y-6">
      <PageHeader
        title="My children"
        description="Attendance is a weighted percentage of the school days on which it was taken."
      />

      {children.isError ? <ErrorBanner error={children.error} /> : null}

      {children.data && children.data.length === 0 ? (
        <Card className="p-4">
          <p className="text-sm text-slate-600">
            No children are linked to your account yet. Please contact the school office.
          </p>
        </Card>
      ) : null}

      {children.data && children.data.length > 0 ? (
        <Card className="p-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Child">
              <Select
                value={studentId}
                onChange={(event) => {
                  setStudentId(event.target.value === "" ? "" : Number(event.target.value));
                  setConfirmation(null);
                }}
              >
                {children.data.map((child) => (
                  <option key={child.studentId} value={child.studentId}>
                    {child.fullName}
                    {child.className ? ` · ${child.className} ${child.section ?? ""}` : ""}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Month">
              <div className="flex items-center gap-2">
                <Button variant="secondary" onClick={() => setMonth(shiftMonth(month, -1))}>
                  ←
                </Button>
                <span className="flex-1 text-center text-sm font-medium">{monthLabel(month)}</span>
                <Button variant="secondary" onClick={() => setMonth(shiftMonth(month, 1))}>
                  →
                </Button>
              </div>
            </Field>
          </div>
        </Card>
      ) : null}

      {attendance.isPending && studentId !== "" ? <Loading label="Loading attendance…" /> : null}
      {attendance.isError ? <ErrorBanner error={attendance.error} /> : null}

      {data ? (
        <>
          <Card className="p-4">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <p className="text-sm text-slate-500">
                  {selected?.fullName ?? data.fullName}
                  {data.className ? ` · ${data.className} ${data.section ?? ""}` : ""}
                </p>
                <p className="mt-1 text-4xl font-semibold">
                  {data.percentage.toFixed(1)}
                  <span className="text-xl text-slate-400">%</span>
                </p>
                <p className="text-xs text-slate-500">
                  {data.presentEquivalentDays} of {data.countedDays} counted days ·{" "}
                  {data.fromDate} → {data.toDate}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <Badge tone="green">Present {data.presentDays}</Badge>
                {data.lateDays > 0 ? <Badge tone="amber">Late {data.lateDays}</Badge> : null}
                {data.halfDays > 0 ? <Badge tone="amber">Half day {data.halfDays}</Badge> : null}
                <Badge tone="red">Absent {data.absentDays}</Badge>
                {data.excusedDays > 0 ? <Badge tone="indigo">Excused {data.excusedDays}</Badge> : null}
              </div>
            </div>

            {data.belowThreshold ? (
              <p className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                Warning: attendance is below the required {data.warningThresholdPercent}%.
              </p>
            ) : data.countedDays > 0 ? (
              <p className="mt-4 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                Attendance is at or above the required {data.warningThresholdPercent}%.
              </p>
            ) : (
              <p className="mt-4 rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600">
                No attendance has been recorded yet.
              </p>
            )}
          </Card>

          <Card className="p-4">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Calendar</h2>
            <div className="mt-3 grid grid-cols-7 gap-1 text-center text-xs font-medium uppercase tracking-wide text-slate-500">
              {WEEKDAY_LABELS.map((label) => (
                <div key={label} className="py-1">
                  {label}
                </div>
              ))}
            </div>
            <div className="mt-1 grid grid-cols-7 gap-1">
              {Array.from({ length: leadingBlanks }, (_, index) => (
                <div key={`blank-${index}`} />
              ))}
              {data.days.map((day) => (
                <button
                  key={day.date}
                  type="button"
                  title={`${day.date}${day.status ? ` · ${STATUS_LABELS[day.status]}` : ""}`}
                  onClick={() => {
                    setQueryDate(day.date);
                    setConfirmation(null);
                  }}
                  className={dayClasses(day)}
                >
                  <span className="text-sm font-medium">{Number(day.date.slice(8, 10))}</span>
                  {day.status ? (
                    <span className="text-[10px] font-semibold uppercase">
                      {STATUS_LABELS[day.status]}
                    </span>
                  ) : !day.working ? (
                    <span className="text-[10px] uppercase text-slate-400">Off</span>
                  ) : null}
                </button>
              ))}
            </div>
            <div className="mt-3 flex flex-wrap gap-3 border-t border-slate-100 pt-3">
              {LEGEND.map((item) => (
                <span key={item.label} className="flex items-center gap-1.5 text-xs text-slate-600">
                  <span className={`h-3 w-3 rounded border ${item.className}`} />
                  {item.label}
                </span>
              ))}
            </div>
          </Card>

          <Card className="p-4">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Absences</h2>
            {data.absences.length === 0 ? (
              <p className="mt-2 text-sm text-slate-600">No absences in this period.</p>
            ) : (
              <ul className="mt-2 divide-y divide-slate-100">
                {data.absences.map((absence) => (
                  <li key={absence.date} className="flex items-center justify-between py-2 text-sm">
                    <span>{absence.date}</span>
                    <div className="flex items-center gap-2">
                      <Badge tone={absence.status === "ABSENT" ? "red" : "amber"}>
                        {STATUS_LABELS[absence.status]}
                      </Badge>
                      <Button
                        variant="ghost"
                        onClick={() => {
                          setQueryDate(absence.date);
                          setConfirmation(null);
                        }}
                      >
                        Dispute
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <Card className="space-y-3 p-4">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
              Raise a query
            </h2>
            <Field label="Date" hint="Pick a date from the calendar or an absence above, or type one.">
              <TextInput
                type="date"
                max={todayIso()}
                value={queryDate}
                onChange={(event) => {
                  setQueryDate(event.target.value);
                  setConfirmation(null);
                }}
              />
            </Field>
            <Field label="What happened?">
              <TextArea
                rows={3}
                value={queryMessage}
                onChange={(event) => {
                  setQueryMessage(event.target.value);
                  setConfirmation(null);
                }}
                placeholder="e.g. She was present but marked absent."
              />
            </Field>

            {raise.isError ? <ErrorBanner error={raise.error} /> : null}
            {confirmation ? <p className="text-sm text-emerald-700">{confirmation}</p> : null}

            <Button
              disabled={raise.isPending || queryDate === "" || queryMessage.trim() === ""}
              onClick={() => raise.mutate()}
            >
              {raise.isPending ? "Sending…" : "Send query"}
            </Button>
          </Card>
        </>
      ) : null}
    </section>
  );
}
