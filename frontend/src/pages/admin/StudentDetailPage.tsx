import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  Badge,
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
import type { EnrollmentInput } from "../../types/api";

const EMPTY_ENROLLMENT: EnrollmentInput = { classId: null, rollNo: null, fromDate: "", toDate: "" };

export function StudentDetailPage() {
  const { studentId } = useParams<{ studentId: string }>();
  const id = Number(studentId);
  const queryClient = useQueryClient();

  const [enrollment, setEnrollment] = useState<EnrollmentInput>(EMPTY_ENROLLMENT);
  const [linkForm, setLinkForm] = useState<{ parentUserId: number | null; relationship: string }>({
    parentUserId: null,
    relationship: "",
  });

  const detail = useQuery({
    queryKey: ["admin", "student", id],
    queryFn: () => adminApi.getStudent(id),
    enabled: Number.isFinite(id),
  });

  const classes = useQuery({
    queryKey: ["admin", "class-options", "all"],
    queryFn: () => adminApi.classOptions(),
  });

  const parents = useQuery({
    queryKey: ["admin", "user-options", "PARENT"],
    queryFn: () => adminApi.userOptions("PARENT"),
  });

  function invalidate() {
    void queryClient.invalidateQueries({ queryKey: ["admin", "student", id] });
  }

  const resetQr = useMutation({
    mutationFn: () => adminApi.regenerateQrToken(id),
    onSuccess: invalidate,
  });

  const addEnrollment = useMutation({
    mutationFn: () => adminApi.enrollStudent(id, enrollment),
    onSuccess: () => {
      setEnrollment(EMPTY_ENROLLMENT);
      invalidate();
      void queryClient.invalidateQueries({ queryKey: ["admin", "students"] });
    },
  });

  const removeEnrollment = useMutation({
    mutationFn: (enrollmentId: number) => adminApi.removeEnrollment(id, enrollmentId),
    onSuccess: invalidate,
  });

  const linkParent = useMutation({
    mutationFn: () =>
      adminApi.linkParentStudent({
        parentUserId: linkForm.parentUserId,
        studentId: id,
        relationship: linkForm.relationship,
      }),
    onSuccess: () => {
      setLinkForm({ parentUserId: null, relationship: "" });
      invalidate();
    },
  });

  const unlinkParent = useMutation({
    mutationFn: (parentUserId: number) => adminApi.unlinkParentStudent(parentUserId, id),
    onSuccess: invalidate,
  });

  if (detail.isPending) {
    return <Loading label="Loading student…" />;
  }

  if (detail.isError || !detail.data) {
    return (
      <section className="space-y-4">
        <ErrorBanner error={detail.error ?? new Error("Student not found")} />
        <Link to="/admin/students" className="text-sm font-medium text-indigo-600 hover:underline">
          Back to students
        </Link>
      </section>
    );
  }

  const student = detail.data;

  return (
    <section className="space-y-6">
      <Link to="/admin/students" className="text-sm font-medium text-indigo-600 hover:underline">
        ← Back to students
      </Link>

      <PageHeader
        title={student.fullName}
        description={`Admission no ${student.admissionNo}`}
        actions={
          student.status === "ACTIVE" ? <Badge tone="green">Active</Badge> : <Badge>Left</Badge>
        }
      />

      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Profile</h2>
          <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
            <dt className="text-slate-500">Date of birth</dt>
            <dd className="font-medium">{student.dob}</dd>
            <dt className="text-slate-500">Admission date</dt>
            <dd className="font-medium">{student.admissionDate}</dd>
            <dt className="text-slate-500">Photo path</dt>
            <dd className="font-medium">{student.photoPath ?? "—"}</dd>
          </dl>
        </Card>

        <Card className="p-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">QR token</h2>
          <p className="mt-3 break-all font-mono text-xs text-slate-700">{student.qrToken ?? "—"}</p>
          <p className="mt-2 text-xs text-slate-500">
            The token is an opaque UUID and never carries personal data. Regenerating invalidates the
            old one immediately.
          </p>
          {resetQr.isError ? <div className="mt-2"><ErrorBanner error={resetQr.error} /></div> : null}
          <div className="mt-3">
            <Button
              disabled={resetQr.isPending}
              onClick={() => {
                if (window.confirm("Regenerate the QR token? The previous token stops working.")) {
                  resetQr.mutate();
                }
              }}
            >
              {resetQr.isPending ? "Regenerating…" : "Regenerate QR token"}
            </Button>
          </div>
        </Card>
      </div>

      <Card className="p-4">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Enrollments</h2>
        {removeEnrollment.isError ? <div className="mt-2"><ErrorBanner error={removeEnrollment.error} /></div> : null}
        <div className="mt-3 overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="py-2">Class</th>
                <th className="py-2">Academic year</th>
                <th className="py-2">Roll</th>
                <th className="py-2">From</th>
                <th className="py-2">To</th>
                <th className="py-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {student.enrollments.length === 0 ? (
                <EmptyRow colSpan={6} message="No enrollments yet." />
              ) : null}
              {student.enrollments.map((row) => (
                <tr key={row.id} className="border-t border-slate-100">
                  <td className="py-2 font-medium">
                    {row.className} {row.section}
                  </td>
                  <td className="py-2 text-slate-600">{row.academicYearName}</td>
                  <td className="py-2 text-slate-600">{row.rollNo ?? "—"}</td>
                  <td className="py-2 text-slate-600">{row.fromDate}</td>
                  <td className="py-2 text-slate-600">{row.toDate ?? "—"}</td>
                  <td className="py-2 text-right">
                    <Button
                      variant="danger"
                      disabled={removeEnrollment.isPending}
                      onClick={() => removeEnrollment.mutate(row.id)}
                    >
                      Remove
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-4">
          <Field label="Class">
            <Select
              value={enrollment.classId ?? ""}
              onChange={(event) =>
                setEnrollment({
                  ...enrollment,
                  classId: event.target.value === "" ? null : Number(event.target.value),
                })
              }
            >
              <option value="">Select a class</option>
              {classes.data?.map((schoolClass) => (
                <option key={schoolClass.id} value={schoolClass.id}>
                  {schoolClass.name} {schoolClass.section} · {schoolClass.academicYearName}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Roll no">
            <TextInput
              type="number"
              min={1}
              value={enrollment.rollNo ?? ""}
              onChange={(event) =>
                setEnrollment({
                  ...enrollment,
                  rollNo: event.target.value === "" ? null : Number(event.target.value),
                })
              }
            />
          </Field>
          <Field label="From date">
            <TextInput
              type="date"
              value={enrollment.fromDate}
              onChange={(event) => setEnrollment({ ...enrollment, fromDate: event.target.value })}
            />
          </Field>
          <Field label="To date" hint="Leave empty for the current enrollment.">
            <TextInput
              type="date"
              value={enrollment.toDate}
              onChange={(event) => setEnrollment({ ...enrollment, toDate: event.target.value })}
            />
          </Field>
        </div>
        {addEnrollment.isError ? <div className="mt-2"><ErrorBanner error={addEnrollment.error} /></div> : null}
        <div className="mt-3">
          <Button disabled={addEnrollment.isPending} onClick={() => addEnrollment.mutate()}>
            {addEnrollment.isPending ? "Adding…" : "Add enrollment"}
          </Button>
        </div>
      </Card>

      <Card className="p-4">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Parents & guardians</h2>
        {unlinkParent.isError ? <div className="mt-2"><ErrorBanner error={unlinkParent.error} /></div> : null}
        <div className="mt-3 overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="py-2">Name</th>
                <th className="py-2">Phone</th>
                <th className="py-2">Email</th>
                <th className="py-2">Relationship</th>
                <th className="py-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {student.parents.length === 0 ? (
                <EmptyRow colSpan={5} message="No linked parents yet." />
              ) : null}
              {student.parents.map((parent) => (
                <tr key={parent.parentUserId} className="border-t border-slate-100">
                  <td className="py-2 font-medium">{parent.parentName ?? "—"}</td>
                  <td className="py-2 text-slate-600">{parent.parentPhone ?? "—"}</td>
                  <td className="py-2 text-slate-600">{parent.parentEmail ?? "—"}</td>
                  <td className="py-2 text-slate-600">{parent.relationship ?? "—"}</td>
                  <td className="py-2 text-right">
                    <Button
                      variant="danger"
                      disabled={unlinkParent.isPending}
                      onClick={() => unlinkParent.mutate(parent.parentUserId)}
                    >
                      Unlink
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-3">
          <Field label="Parent">
            <Select
              value={linkForm.parentUserId ?? ""}
              onChange={(event) =>
                setLinkForm({
                  ...linkForm,
                  parentUserId: event.target.value === "" ? null : Number(event.target.value),
                })
              }
            >
              <option value="">Select a parent</option>
              {parents.data?.map((parent) => (
                <option key={parent.id} value={parent.id}>
                  {parent.name}
                  {parent.phone ? ` · ${parent.phone}` : ""}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Relationship">
            <TextInput
              value={linkForm.relationship}
              onChange={(event) => setLinkForm({ ...linkForm, relationship: event.target.value })}
              placeholder="MOTHER"
            />
          </Field>
          <div className="flex items-end">
            <Button disabled={linkParent.isPending} onClick={() => linkParent.mutate()}>
              {linkParent.isPending ? "Linking…" : "Link parent"}
            </Button>
          </div>
        </div>
        {linkParent.isError ? <div className="mt-2"><ErrorBanner error={linkParent.error} /></div> : null}
      </Card>
    </section>
  );
}
