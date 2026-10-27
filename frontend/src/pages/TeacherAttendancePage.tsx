import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { useCallback, useEffect, useMemo, useState } from "react";
import { QrScanner } from "../components/QrScanner";
import {
  Badge,
  Button,
  Card,
  CheckboxField,
  ErrorBanner,
  Field,
  Loading,
  PageHeader,
  Select,
  TextInput,
} from "../components/ui";
import { useAuth } from "../auth/useAuth";
import * as attendanceApi from "../lib/attendanceApi";
import { toApiError } from "../lib/api";
import { enqueue, listQueued, queuedCount, removeAt } from "../lib/offlineQueue";
import type { AttendanceScanResult, AttendanceSource, AttendanceStatus } from "../types/api";

const STATUSES: Array<{ value: AttendanceStatus; label: string; short: string }> = [
  { value: "PRESENT", label: "Present", short: "P" },
  { value: "ABSENT", label: "Absent", short: "A" },
  { value: "LATE", label: "Late", short: "L" },
  { value: "HALF_DAY", label: "Half day", short: "H" },
  { value: "EXCUSED", label: "Excused", short: "E" },
];

const SHORT_LABEL: Record<AttendanceStatus, string> = {
  PRESENT: "Present",
  ABSENT: "Absent",
  LATE: "Late",
  HALF_DAY: "Half day",
  EXCUSED: "Excused",
};

type ScanKind = "marked" | "already" | "warning" | "queued" | "error";

interface ScannedEntry {
  key: string;
  kind: ScanKind;
  fullName: string;
  photoPath?: string | null;
  message: string;
  time: string;
}

function todayIso(): string {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

function Avatar({ name, photoPath }: { name: string; photoPath?: string | null }) {
  const src = photoPath && photoPath.startsWith("http") ? photoPath : null;
  if (src) {
    return <img src={src} alt={name} className="h-10 w-10 rounded-full object-cover" />;
  }
  const initials = name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
  return (
    <span className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-200 text-sm font-semibold text-slate-600">
      {initials || "?"}
    </span>
  );
}

export function TeacherAttendancePage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isAdmin = user?.roles.includes("ADMIN") ?? false;

  const [date, setDate] = useState(todayIso);
  const [classId, setClassId] = useState<number | "">("");
  const [statuses, setStatuses] = useState<Record<number, AttendanceStatus>>({});
  const [source, setSource] = useState<AttendanceSource>("MANUAL");
  const [overrideHoliday, setOverrideHoliday] = useState(false);
  const [scanned, setScanned] = useState<ScannedEntry[]>([]);
  const [manualToken, setManualToken] = useState("");
  const [cameraOn, setCameraOn] = useState(false);
  const [unsynced, setUnsynced] = useState(() => queuedCount());
  const [message, setMessage] = useState<string | null>(null);

  const classes = useQuery({
    queryKey: ["attendance", "classes", date],
    queryFn: () => attendanceApi.markableClasses(date),
  });

  useEffect(() => {
    if (classId === "" && classes.data && classes.data.length > 0) {
      setClassId(classes.data[0].classId);
    }
  }, [classes.data, classId]);

  const roster = useQuery({
    queryKey: ["attendance", "roster", classId, date],
    queryFn: () => attendanceApi.attendanceRoster(Number(classId), date),
    enabled: classId !== "",
  });

  useEffect(() => {
    if (!roster.data) {
      return;
    }
    const next: Record<number, AttendanceStatus> = {};
    for (const row of roster.data.students) {
      if (row.status) {
        next[row.studentId] = row.status;
      }
    }
    setStatuses(next);
  }, [roster.data]);

  const counts = useMemo(() => {
    const result = { total: 0, present: 0, absent: 0, late: 0, halfDay: 0, excused: 0, unmarked: 0 };
    const rows = roster.data?.students ?? [];
    result.total = rows.length;
    for (const row of rows) {
      const status = statuses[row.studentId];
      if (!status) {
        result.unmarked++;
      } else if (status === "PRESENT") {
        result.present++;
      } else if (status === "ABSENT") {
        result.absent++;
      } else if (status === "LATE") {
        result.late++;
      } else if (status === "HALF_DAY") {
        result.halfDay++;
      } else {
        result.excused++;
      }
    }
    return result;
  }, [roster.data, statuses]);

  const record = useCallback((result: AttendanceScanResult, time: string) => {
    const kind: ScanKind = result.warning ? "warning" : result.alreadyMarked ? "already" : "marked";
    setScanned((previous) => [
      {
        key: `${result.studentId}-${Date.now()}`,
        kind,
        fullName: result.fullName,
        photoPath: result.photoPath,
        message: result.warning ?? result.message,
        time,
      },
      ...previous,
    ]);
  }, []);

  const handleScan = useCallback(
    async (token: string) => {
      if (classId === "") {
        return;
      }
      const payload = { qrToken: token, classId: Number(classId), date };
      const time = new Date().toLocaleTimeString();
      try {
        const result = await attendanceApi.scanAttendance(payload);
        record(result, time);
        if (result.marked) {
          setStatuses((previous) => ({ ...previous, [result.studentId]: "PRESENT" }));
          void queryClient.invalidateQueries({ queryKey: ["attendance", "roster", classId, date] });
        }
      } catch (error) {
        if (axios.isAxiosError(error) && !error.response) {
          enqueue(payload);
          setUnsynced(queuedCount());
          setScanned((previous) => [
            {
              key: `queued-${Date.now()}`,
              kind: "queued",
              fullName: "Saved offline",
              message: "No connection — the scan is queued and will sync.",
              time,
            },
            ...previous,
          ]);
          return;
        }
        setScanned((previous) => [
          {
            key: `error-${Date.now()}`,
            kind: "error",
            fullName: "Scan failed",
            message: toApiError(error).message,
            time,
          },
          ...previous,
        ]);
      }
    },
    [classId, date, record, queryClient],
  );

  const submit = useMutation({
    mutationFn: () => {
      const entries = Object.entries(statuses).map(([id, status]) => ({
        studentId: Number(id),
        status,
      }));
      return attendanceApi.submitAttendance(Number(classId), {
        date,
        source,
        overrideHoliday,
        entries,
      });
    },
    onSuccess: (data) => {
      setMessage(`Saved. ${data.counts.total - data.counts.unmarked} of ${data.counts.total} marked.`);
      void queryClient.invalidateQueries({ queryKey: ["attendance", "roster", classId, date] });
    },
  });

  const sync = useMutation({
    mutationFn: async () => {
      const queued = listQueued();
      for (let index = queued.length - 1; index >= 0; index--) {
        const item = queued[index];
        try {
          await attendanceApi.scanAttendance({
            qrToken: item.qrToken,
            classId: item.classId,
            date: item.date,
          });
          removeAt(index);
        } catch (error) {
          if (axios.isAxiosError(error) && !error.response) {
            break;
          }
          removeAt(index);
        }
      }
      setUnsynced(queuedCount());
      void queryClient.invalidateQueries({ queryKey: ["attendance", "roster", classId, date] });
    },
  });

  function toggle(studentId: number, status: AttendanceStatus) {
    setStatuses((previous) => {
      const next = { ...previous };
      if (next[studentId] === status) {
        delete next[studentId];
      } else {
        next[studentId] = status;
      }
      return next;
    });
  }

  const entriesCount = Object.keys(statuses).length;

  return (
    <section className="mx-auto max-w-2xl space-y-4">
      <PageHeader
        title="Attendance"
        description="Scan student QR codes or tap the roster. Unmarked is not the same as absent."
        actions={
          unsynced > 0 ? (
            <Button variant="secondary" disabled={sync.isPending} onClick={() => sync.mutate()}>
              {sync.isPending ? "Syncing…" : `Sync ${unsynced} unsynced`}
            </Button>
          ) : (
            <Badge tone="green">All synced</Badge>
          )
        }
      />

      {unsynced > 0 ? (
        <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          {unsynced} scan{unsynced === 1 ? "" : "s"} saved on this device and not yet synced.
        </p>
      ) : null}

      <Card className="space-y-3 p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Date">
            <TextInput
              type="date"
              value={date}
              onChange={(event) => {
                setDate(event.target.value);
                setMessage(null);
              }}
            />
          </Field>
          <Field label="Class">
            <Select
              value={classId}
              onChange={(event) => {
                setClassId(event.target.value === "" ? "" : Number(event.target.value));
                setMessage(null);
              }}
            >
              <option value="">Select a class</option>
              {classes.data?.map((item) => (
                <option key={item.classId} value={item.classId}>
                  {item.className} {item.section} · {item.role.replace("_", " ").toLowerCase()}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        {classes.isError ? <ErrorBanner error={classes.error} /> : null}

        {classes.data && classes.data.length === 0 ? (
          <p className="text-sm text-slate-600">
            You are not a class teacher (or substitute) for any class on this date.
          </p>
        ) : null}
      </Card>

      {classId !== "" ? (
        <>
          <Card className="space-y-3 p-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
                Scan QR codes
              </h2>
              <Button variant="secondary" onClick={() => setCameraOn((value) => !value)}>
                {cameraOn ? "Stop camera" : "Start camera"}
              </Button>
            </div>

            {cameraOn ? <QrScanner active={cameraOn} onDecode={(token) => void handleScan(token)} /> : null}

            <div className="flex items-end gap-2">
              <Field label="Manual QR token">
                <TextInput
                  value={manualToken}
                  onChange={(event) => setManualToken(event.target.value)}
                  placeholder="Paste the token shown under the QR"
                />
              </Field>
              <Button
                disabled={manualToken.trim() === ""}
                onClick={() => {
                  void handleScan(manualToken.trim());
                  setManualToken("");
                }}
              >
                Mark
              </Button>
            </div>

            {scanned.length > 0 ? (
              <ul className="divide-y divide-slate-100 rounded-md border border-slate-200">
                {scanned.map((entry) => (
                  <li key={entry.key} className="flex items-center gap-3 px-3 py-2">
                    <Avatar name={entry.fullName} photoPath={entry.photoPath} />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{entry.fullName}</p>
                      <p className="truncate text-xs text-slate-500">{entry.message}</p>
                    </div>
                    <span className="shrink-0 text-xs text-slate-400">{entry.time}</span>
                    <Badge
                      tone={
                        entry.kind === "marked"
                          ? "green"
                          : entry.kind === "already"
                            ? "slate"
                            : entry.kind === "warning"
                              ? "amber"
                              : entry.kind === "queued"
                                ? "indigo"
                                : "red"
                      }
                    >
                      {entry.kind}
                    </Badge>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-xs text-slate-500">Scanned students appear here with name, photo and time.</p>
            )}
          </Card>

          <Card className="space-y-3 p-4">
            <div className="flex flex-wrap gap-2">
              <Badge tone="green">Present {counts.present}</Badge>
              <Badge tone="red">Absent {counts.absent}</Badge>
              {counts.late > 0 ? <Badge tone="amber">Late {counts.late}</Badge> : null}
              {counts.halfDay > 0 ? <Badge tone="amber">Half day {counts.halfDay}</Badge> : null}
              {counts.excused > 0 ? <Badge tone="indigo">Excused {counts.excused}</Badge> : null}
              <Badge tone="slate">Unmarked {counts.unmarked}</Badge>
              <Badge tone="indigo">of {counts.total}</Badge>
            </div>

            {roster.data && !roster.data.workingDay ? (
              <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                {roster.data.date} is not a working day.
                {isAdmin ? " You can override it below." : " Only an admin can override this."}
              </p>
            ) : null}

            {roster.isPending ? <Loading label="Loading roster…" /> : null}
            {roster.isError ? <ErrorBanner error={roster.error} /> : null}

            <ul className="divide-y divide-slate-100">
              {roster.data?.students.map((row) => (
                <li key={row.studentId} className="flex flex-wrap items-center gap-2 py-2">
                  <Avatar name={row.fullName} photoPath={row.photoPath} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{row.fullName}</p>
                    <p className="text-xs text-slate-500">
                      {row.rollNo != null ? `Roll ${row.rollNo} · ` : ""}
                      {row.admissionNo}
                      {statuses[row.studentId] ? ` · ${SHORT_LABEL[statuses[row.studentId]]}` : " · unmarked"}
                    </p>
                  </div>
                  <div className="flex gap-1">
                    {STATUSES.map((option) => {
                      const active = statuses[row.studentId] === option.value;
                      return (
                        <button
                          key={option.value}
                          type="button"
                          title={option.label}
                          aria-label={`${option.label} ${row.fullName}`}
                          aria-pressed={active}
                          onClick={() => toggle(row.studentId, option.value)}
                          className={`h-9 w-9 rounded-md border text-sm font-semibold ${
                            active
                              ? "border-indigo-600 bg-indigo-600 text-white"
                              : "border-slate-300 bg-white text-slate-600 hover:bg-slate-100"
                          }`}
                        >
                          {option.short}
                        </button>
                      );
                    })}
                  </div>
                </li>
              ))}
            </ul>

            <div className="grid gap-3 border-t border-slate-100 pt-3 sm:grid-cols-2">
              <Field label="Source">
                <Select
                  value={source}
                  onChange={(event) => setSource(event.target.value as AttendanceSource)}
                >
                  <option value="MANUAL">Manual</option>
                  <option value="PAPER_BACKFILL">Paper backfill</option>
                </Select>
              </Field>
              {isAdmin ? (
                <div className="flex items-end">
                  <CheckboxField
                    label="Override holiday / weekend"
                    checked={overrideHoliday}
                    onChange={setOverrideHoliday}
                  />
                </div>
              ) : null}
            </div>

            {submit.isError ? <ErrorBanner error={submit.error} /> : null}
            {message ? <p className="text-sm text-emerald-700">{message}</p> : null}

            <Button
              className="w-full"
              disabled={submit.isPending || entriesCount === 0}
              onClick={() => submit.mutate()}
            >
              {submit.isPending ? "Submitting…" : `Submit ${entriesCount} marked`}
            </Button>
          </Card>
        </>
      ) : null}
    </section>
  );
}
