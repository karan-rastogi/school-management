import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Button,
  Card,
  ErrorBanner,
  Field,
  Loading,
  Modal,
  PageHeader,
  Select,
  TextInput,
} from "../../components/ui";
import * as adminApi from "../../lib/adminApi";
import type { CalendarDay, SchoolCalendarDayType } from "../../types/api";

const WEEKDAY_LABELS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

const SHORT_LABELS: Record<SchoolCalendarDayType, string> = {
  WORKING: "Working",
  HOLIDAY: "Holiday",
  HALF_DAY: "Half day",
  EXAM: "Exam",
};

const TYPE_OPTIONS: Array<{ value: "" | SchoolCalendarDayType; label: string }> = [
  { value: "", label: "No override (use the default)" },
  { value: "WORKING", label: "Working day (e.g. a working Saturday or Sunday)" },
  { value: "HOLIDAY", label: "Holiday" },
  { value: "HALF_DAY", label: "Half day" },
  { value: "EXAM", label: "Exam day" },
];

const LEGEND: Array<{ className: string; label: string }> = [
  { className: "bg-white border-slate-200", label: "Working (default)" },
  { className: "bg-emerald-50 border-emerald-200", label: "Working (override)" },
  { className: "bg-slate-100 border-slate-200", label: "Weekend / non-working" },
  { className: "bg-red-50 border-red-200", label: "Holiday" },
  { className: "bg-amber-50 border-amber-200", label: "Half day" },
  { className: "bg-indigo-50 border-indigo-200", label: "Exam" },
];

function iso(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function addDays(date: Date, days: number): Date {
  const copy = new Date(date);
  copy.setUTCDate(copy.getUTCDate() + days);
  return copy;
}

function monthLabel(date: Date): string {
  return date.toLocaleDateString("en-GB", { month: "long", year: "numeric", timeZone: "UTC" });
}

function cellClasses(day: CalendarDay | undefined, outside: boolean): string {
  const base = "flex h-20 flex-col items-start gap-1 rounded-md border p-2 text-left transition hover:ring-2 hover:ring-indigo-300";
  const dim = outside ? " opacity-50" : "";
  if (!day) {
    return `${base} border-slate-200 bg-white${dim}`;
  }
  switch (day.type) {
    case "HOLIDAY":
      return `${base} border-red-200 bg-red-50 text-red-700${dim}`;
    case "HALF_DAY":
      return `${base} border-amber-200 bg-amber-50 text-amber-700${dim}`;
    case "EXAM":
      return `${base} border-indigo-200 bg-indigo-50 text-indigo-700${dim}`;
    case "WORKING":
      return `${base} border-emerald-200 bg-emerald-50 text-emerald-700${dim}`;
    default:
      return day.working
        ? `${base} border-slate-200 bg-white text-slate-700${dim}`
        : `${base} border-slate-200 bg-slate-100 text-slate-400${dim}`;
  }
}

export function CalendarPage() {
  const queryClient = useQueryClient();
  const today = new Date();
  const [cursor, setCursor] = useState(
    () => new Date(Date.UTC(today.getFullYear(), today.getMonth(), 1)),
  );
  const [selected, setSelected] = useState<CalendarDay | null>(null);
  const [form, setForm] = useState<{ type: "" | SchoolCalendarDayType; description: string }>({
    type: "",
    description: "",
  });

  const monthStart = cursor;
  const gridStart = addDays(monthStart, -monthStart.getUTCDay());
  const monthEnd = new Date(Date.UTC(monthStart.getUTCFullYear(), monthStart.getUTCMonth() + 1, 0));

  const range = useQuery({
    queryKey: ["admin", "calendar", iso(gridStart), iso(addDays(gridStart, 41))],
    queryFn: () => adminApi.calendarRange(iso(gridStart), iso(addDays(gridStart, 41))),
  });

  const workingDays = useQuery({
    queryKey: ["admin", "calendar", "working-days", iso(monthStart), iso(monthEnd)],
    queryFn: () => adminApi.workingDays(iso(monthStart), iso(monthEnd)),
  });

  const save = useMutation({
    mutationFn: async () => {
      if (!selected) {
        return;
      }
      if (form.type === "") {
        await adminApi.clearCalendarDay(selected.date);
        return;
      }
      await adminApi.markCalendarDay(selected.date, { type: form.type, description: form.description });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "calendar"] });
      setSelected(null);
    },
  });

  const byDate = new Map((range.data ?? []).map((day) => [day.date, day]));
  const cells = Array.from({ length: 42 }, (_, index) => addDays(gridStart, index));

  function open(day: CalendarDay) {
    setSelected(day);
    setForm({ type: day.type ?? "", description: day.description ?? "" });
    save.reset();
  }

  function shiftMonth(delta: number) {
    setCursor((current) => new Date(Date.UTC(current.getUTCFullYear(), current.getUTCMonth() + delta, 1)));
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title="School calendar"
        description="Saturdays and Sundays are non-working by default. Mark a date as working, a holiday, a half day or an exam — the working-day count drives attendance percentages."
        actions={
          <>
            <Button variant="secondary" onClick={() => shiftMonth(-1)}>
              ← Previous
            </Button>
            <Button variant="secondary" onClick={() => shiftMonth(1)}>
              Next →
            </Button>
          </>
        }
      />

      {range.isError ? <ErrorBanner error={range.error} /> : null}
      {save.isError ? <ErrorBanner error={save.error} /> : null}

      <Card className="p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold">{monthLabel(monthStart)}</h2>
          {workingDays.data ? (
            <span className="text-sm text-slate-600">
              Working days this month:{" "}
              <span className="font-semibold text-slate-900">{workingDays.data.workingDays}</span>
            </span>
          ) : null}
        </div>

        {range.isPending ? (
          <div className="mt-4">
            <Loading label="Loading calendar…" />
          </div>
        ) : (
          <>
            <div className="mt-4 grid grid-cols-7 gap-1 text-center text-xs font-medium uppercase tracking-wide text-slate-500">
              {WEEKDAY_LABELS.map((label) => (
                <div key={label} className="py-1">
                  {label}
                </div>
              ))}
            </div>

            <div className="mt-1 grid grid-cols-7 gap-1">
              {cells.map((date) => {
                const key = iso(date);
                const day = byDate.get(key);
                const outside = date.getUTCMonth() !== monthStart.getUTCMonth();
                return (
                  <button
                    key={key}
                    type="button"
                    onClick={() => open(day ?? { date: key, type: null, working: false, description: null })}
                    className={cellClasses(day, outside)}
                  >
                    <span className="text-sm font-medium">{date.getUTCDate()}</span>
                    {day?.type ? (
                      <span className="text-[10px] font-semibold uppercase">{SHORT_LABELS[day.type]}</span>
                    ) : day && !day.working ? (
                      <span className="text-[10px] uppercase text-slate-400">Off</span>
                    ) : null}
                  </button>
                );
              })}
            </div>
          </>
        )}

        <div className="mt-4 flex flex-wrap gap-3 border-t border-slate-100 pt-3">
          {LEGEND.map((item) => (
            <span key={item.label} className="flex items-center gap-1.5 text-xs text-slate-600">
              <span className={`h-3 w-3 rounded border ${item.className}`} />
              {item.label}
            </span>
          ))}
        </div>
      </Card>

      <Modal
        open={selected !== null}
        title={selected ? `Set day: ${selected.date}` : "Set day"}
        onClose={() => setSelected(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setSelected(null)}>
              Cancel
            </Button>
            <Button disabled={save.isPending} onClick={() => save.mutate()}>
              {save.isPending ? "Saving…" : "Save"}
            </Button>
          </>
        }
      >
        <Field label="Day type">
          <Select
            value={form.type}
            onChange={(event) =>
              setForm({ ...form, type: event.target.value as "" | SchoolCalendarDayType })
            }
          >
            {TYPE_OPTIONS.map((option) => (
              <option key={option.value || "none"} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Note" hint="Optional — shown in the calendar and audit history.">
          <TextInput
            value={form.description}
            onChange={(event) => setForm({ ...form, description: event.target.value })}
            placeholder="Independence Day"
          />
        </Field>
        <p className="text-xs text-slate-500">
          Choosing “No override” clears any entry and lets the date fall back to its default.
        </p>
      </Modal>
    </section>
  );
}
