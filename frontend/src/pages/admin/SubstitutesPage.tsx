import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Button,
  Card,
  EmptyRow,
  ErrorBanner,
  Field,
  Loading,
  PageHeader,
  Select,
  TextInput,
} from "../../components/ui";
import * as adminApi from "../../lib/adminApi";
import * as attendanceApi from "../../lib/attendanceApi";

function iso(date: Date): string {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

export function SubstitutesPage() {
  const queryClient = useQueryClient();
  const [classId, setClassId] = useState<number | "">("");
  const [teacherUserId, setTeacherUserId] = useState<number | "">("");
  const [validFrom, setValidFrom] = useState(() => iso(new Date()));
  const [validUntil, setValidUntil] = useState(() => iso(new Date(Date.now() + 7 * 86_400_000)));
  const [reason, setReason] = useState("");

  const classes = useQuery({
    queryKey: ["admin", "class-options", "all"],
    queryFn: () => adminApi.classOptions(),
  });

  const teachers = useQuery({
    queryKey: ["admin", "user-options", "TEACHER"],
    queryFn: () => adminApi.userOptions("TEACHER"),
  });

  const permissions = useQuery({
    queryKey: ["admin", "substitute-permissions", classId],
    queryFn: () => attendanceApi.listSubstitutePermissions(Number(classId)),
    enabled: classId !== "",
  });

  const grant = useMutation({
    mutationFn: () =>
      attendanceApi.grantSubstitutePermission(Number(classId), {
        teacherUserId: teacherUserId === "" ? null : teacherUserId,
        validFrom,
        validUntil,
        reason,
      }),
    onSuccess: () => {
      setReason("");
      setTeacherUserId("");
      void queryClient.invalidateQueries({ queryKey: ["admin", "substitute-permissions", classId] });
    },
  });

  const revoke = useMutation({
    mutationFn: (id: number) => attendanceApi.revokeSubstitutePermission(id),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ["admin", "substitute-permissions", classId] }),
  });

  return (
    <section className="space-y-6">
      <PageHeader
        title="Substitute permissions"
        description="Temporarily let another teacher mark attendance for a class while its class teacher is away."
      />

      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={classId}
          onChange={(event) => setClassId(event.target.value === "" ? "" : Number(event.target.value))}
          className="max-w-sm"
        >
          <option value="">Select a class</option>
          {classes.data?.map((schoolClass) => (
            <option key={schoolClass.id} value={schoolClass.id}>
              {schoolClass.name} {schoolClass.section} · {schoolClass.academicYearName}
            </option>
          ))}
        </Select>
      </div>

      {classId === "" ? (
        <p className="text-sm text-slate-500">Choose a class to see and grant substitute permissions.</p>
      ) : (
        <Card className="p-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
            Active grants
          </h2>

          {permissions.isPending ? (
            <div className="mt-3">
              <Loading label="Loading permissions…" />
            </div>
          ) : (
            <div className="mt-3 overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="py-2">Teacher</th>
                    <th className="py-2">From</th>
                    <th className="py-2">Until</th>
                    <th className="py-2">Reason</th>
                    <th className="py-2 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {permissions.data && permissions.data.length === 0 ? (
                    <EmptyRow colSpan={5} message="No substitute permissions for this class." />
                  ) : null}
                  {permissions.data?.map((permission) => (
                    <tr key={permission.id} className="border-t border-slate-100">
                      <td className="py-2 font-medium">{permission.teacherName}</td>
                      <td className="py-2 text-slate-600">{permission.validFrom}</td>
                      <td className="py-2 text-slate-600">{permission.validUntil}</td>
                      <td className="py-2 text-slate-600">{permission.reason ?? "—"}</td>
                      <td className="py-2 text-right">
                        <Button
                          variant="danger"
                          disabled={revoke.isPending}
                          onClick={() => revoke.mutate(permission.id)}
                        >
                          Revoke
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {permissions.isError ? <div className="mt-2"><ErrorBanner error={permissions.error} /></div> : null}
          {revoke.isError ? <div className="mt-2"><ErrorBanner error={revoke.error} /></div> : null}

          <div className="mt-6 space-y-3 border-t border-slate-100 pt-4">
            <h3 className="text-sm font-semibold text-slate-700">Grant a permission</h3>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Teacher">
                <Select
                  value={teacherUserId}
                  onChange={(event) =>
                    setTeacherUserId(event.target.value === "" ? "" : Number(event.target.value))
                  }
                >
                  <option value="">Select a teacher</option>
                  {teachers.data?.map((teacher) => (
                    <option key={teacher.id} value={teacher.id}>
                      {teacher.name}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Reason">
                <TextInput
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  placeholder="Class teacher on leave"
                />
              </Field>
              <Field label="From">
                <TextInput type="date" value={validFrom} onChange={(event) => setValidFrom(event.target.value)} />
              </Field>
              <Field label="Until">
                <TextInput
                  type="date"
                  value={validUntil}
                  onChange={(event) => setValidUntil(event.target.value)}
                />
              </Field>
            </div>
            {grant.isError ? <ErrorBanner error={grant.error} /> : null}
            <Button
              disabled={grant.isPending || teacherUserId === ""}
              onClick={() => grant.mutate()}
            >
              {grant.isPending ? "Granting…" : "Grant permission"}
            </Button>
          </div>
        </Card>
      )}
    </section>
  );
}
