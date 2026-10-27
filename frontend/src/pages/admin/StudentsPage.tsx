import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import {
  Badge,
  Button,
  Card,
  EmptyRow,
  ErrorBanner,
  Field,
  Loading,
  Modal,
  PageHeader,
  Pagination,
  Select,
  TextInput,
} from "../../components/ui";
import * as adminApi from "../../lib/adminApi";
import { useDebouncedValue } from "../../lib/hooks";
import type { Student, StudentInput, StudentStatus } from "../../types/api";

const EMPTY: StudentInput = {
  fullName: "",
  admissionNo: "",
  dob: "",
  photoPath: "",
  admissionDate: "",
  status: "ACTIVE",
};
const PAGE_SIZE = 20;

export function StudentsPage() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"" | StudentStatus>("");
  const [classFilter, setClassFilter] = useState<number | "">("");
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(query);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Student | null>(null);
  const [form, setForm] = useState<StudentInput>(EMPTY);

  const classes = useQuery({
    queryKey: ["admin", "class-options", "all"],
    queryFn: () => adminApi.classOptions(),
  });

  const students = useQuery({
    queryKey: ["admin", "students", debounced, statusFilter, classFilter, page],
    queryFn: () =>
      adminApi.listStudents({
        query: debounced || undefined,
        status: statusFilter === "" ? undefined : statusFilter,
        classId: classFilter === "" ? undefined : classFilter,
        page,
      }),
    placeholderData: keepPreviousData,
  });

  const save = useMutation({
    mutationFn: () =>
      editing ? adminApi.updateStudent(editing.id, form) : adminApi.createStudent(form),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "students"] });
      setFormOpen(false);
      setEditing(null);
    },
  });

  const resetQr = useMutation({
    mutationFn: (id: number) => adminApi.regenerateQrToken(id),
    onSuccess: (token) => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "students"] });
      window.alert(`New QR token:\n${token}`);
    },
  });

  function openCreate() {
    setEditing(null);
    setForm(EMPTY);
    save.reset();
    setFormOpen(true);
  }

  function openEdit(student: Student) {
    setEditing(student);
    setForm({
      fullName: student.fullName,
      admissionNo: student.admissionNo,
      dob: student.dob,
      photoPath: student.photoPath ?? "",
      admissionDate: student.admissionDate,
      status: student.status,
    });
    save.reset();
    setFormOpen(true);
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title="Students"
        description="Search, add and edit student records. Open a student to manage enrollments and parents."
        actions={
          <>
            <Link
              to="/admin/import"
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
            >
              Import CSV
            </Link>
            <Button onClick={openCreate}>New student</Button>
          </>
        }
      />

      <div className="flex flex-wrap items-center gap-2">
        <TextInput
          placeholder="Search by name or admission number…"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setPage(0);
          }}
          className="max-w-xs"
        />
        <Select
          value={statusFilter}
          onChange={(event) => {
            setStatusFilter(event.target.value as "" | StudentStatus);
            setPage(0);
          }}
          className="max-w-[10rem]"
        >
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="LEFT">Left</option>
        </Select>
        <Select
          value={classFilter}
          onChange={(event) => {
            setClassFilter(event.target.value === "" ? "" : Number(event.target.value));
            setPage(0);
          }}
          className="max-w-xs"
        >
          <option value="">All classes</option>
          {classes.data?.map((schoolClass) => (
            <option key={schoolClass.id} value={schoolClass.id}>
              {schoolClass.name} {schoolClass.section} · {schoolClass.academicYearName}
            </option>
          ))}
        </Select>
      </div>

      {resetQr.isError ? <ErrorBanner error={resetQr.error} /> : null}
      {students.isError ? <ErrorBanner error={students.error} /> : null}

      <Card>
        {students.isPending ? (
          <div className="p-4">
            <Loading label="Loading students…" />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-2">Name</th>
                  <th className="px-4 py-2">Admission no</th>
                  <th className="px-4 py-2">Class</th>
                  <th className="px-4 py-2">Roll</th>
                  <th className="px-4 py-2">Status</th>
                  <th className="px-4 py-2 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {students.data && students.data.content.length === 0 ? (
                  <EmptyRow colSpan={6} message="No students found." />
                ) : null}
                {students.data?.content.map((student) => (
                  <tr key={student.id} className="border-t border-slate-100">
                    <td className="px-4 py-2 font-medium">
                      <Link to={`/admin/students/${student.id}`} className="text-indigo-600 hover:underline">
                        {student.fullName}
                      </Link>
                    </td>
                    <td className="px-4 py-2 text-slate-600">{student.admissionNo}</td>
                    <td className="px-4 py-2 text-slate-600">
                      {student.currentClassName
                        ? `${student.currentClassName} ${student.currentSection}`
                        : "—"}
                    </td>
                    <td className="px-4 py-2 text-slate-600">{student.rollNo ?? "—"}</td>
                    <td className="px-4 py-2">
                      {student.status === "ACTIVE" ? (
                        <Badge tone="green">Active</Badge>
                      ) : (
                        <Badge>Left</Badge>
                      )}
                    </td>
                    <td className="px-4 py-2 text-right">
                      <div className="flex justify-end gap-2">
                        <Link
                          to={`/admin/students/${student.id}`}
                          className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
                        >
                          View
                        </Link>
                        <Button variant="secondary" onClick={() => openEdit(student)}>
                          Edit
                        </Button>
                        <Button
                          variant="secondary"
                          disabled={resetQr.isPending}
                          onClick={() => {
                            if (window.confirm(`Regenerate the QR token for ${student.fullName}?`)) {
                              resetQr.mutate(student.id);
                            }
                          }}
                        >
                          Reset QR
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {students.data ? (
        <Pagination
          page={students.data.page}
          pageSize={PAGE_SIZE}
          totalPages={students.data.totalPages}
          totalElements={students.data.totalElements}
          onPageChange={setPage}
        />
      ) : null}

      <Modal
        open={formOpen}
        title={editing ? `Edit ${editing.fullName}` : "New student"}
        onClose={() => setFormOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setFormOpen(false)}>
              Cancel
            </Button>
            <Button disabled={save.isPending} onClick={() => save.mutate()}>
              {save.isPending ? "Saving…" : "Save"}
            </Button>
          </>
        }
      >
        {save.isError ? <ErrorBanner error={save.error} /> : null}
        <Field label="Full name">
          <TextInput
            value={form.fullName}
            onChange={(event) => setForm({ ...form, fullName: event.target.value })}
          />
        </Field>
        <Field label="Admission number">
          <TextInput
            value={form.admissionNo}
            onChange={(event) => setForm({ ...form, admissionNo: event.target.value })}
            placeholder="ADM-2025-001"
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Date of birth">
            <TextInput
              type="date"
              value={form.dob}
              onChange={(event) => setForm({ ...form, dob: event.target.value })}
            />
          </Field>
          <Field label="Admission date">
            <TextInput
              type="date"
              value={form.admissionDate}
              onChange={(event) => setForm({ ...form, admissionDate: event.target.value })}
            />
          </Field>
        </div>
        <Field label="Photo path" hint="Optional. Storage path only — no images are uploaded here.">
          <TextInput
            value={form.photoPath}
            onChange={(event) => setForm({ ...form, photoPath: event.target.value })}
          />
        </Field>
        <Field label="Status">
          <Select
            value={form.status}
            onChange={(event) => setForm({ ...form, status: event.target.value as StudentStatus })}
          >
            <option value="ACTIVE">Active</option>
            <option value="LEFT">Left</option>
          </Select>
        </Field>
      </Modal>
    </section>
  );
}
