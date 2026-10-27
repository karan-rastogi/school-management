import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Badge,
  Button,
  Card,
  CheckboxField,
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
import type { TeacherAssignment, TeacherAssignmentInput } from "../../types/api";

const EMPTY: TeacherAssignmentInput = {
  teacherUserId: null,
  classId: null,
  subjectName: "",
  classTeacher: false,
  active: true,
};
const PAGE_SIZE = 20;

export function TeacherAssignmentsPage() {
  const queryClient = useQueryClient();
  const [classFilter, setClassFilter] = useState<number | "">("");
  const [teacherFilter, setTeacherFilter] = useState<number | "">("");
  const [page, setPage] = useState(0);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<TeacherAssignment | null>(null);
  const [form, setForm] = useState<TeacherAssignmentInput>(EMPTY);

  const teachers = useQuery({
    queryKey: ["admin", "user-options", "TEACHER"],
    queryFn: () => adminApi.userOptions("TEACHER"),
  });

  const classes = useQuery({
    queryKey: ["admin", "class-options", "all"],
    queryFn: () => adminApi.classOptions(),
  });

  const assignments = useQuery({
    queryKey: ["admin", "teacher-assignments", classFilter, teacherFilter, page],
    queryFn: () =>
      adminApi.listTeacherAssignments({
        classId: classFilter === "" ? undefined : classFilter,
        teacherUserId: teacherFilter === "" ? undefined : teacherFilter,
        page,
      }),
    placeholderData: keepPreviousData,
  });

  const save = useMutation({
    mutationFn: () =>
      editing
        ? adminApi.updateTeacherAssignment(editing.id, form)
        : adminApi.createTeacherAssignment(form),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "teacher-assignments"] });
      setFormOpen(false);
      setEditing(null);
    },
  });

  const remove = useMutation({
    mutationFn: (id: number) => adminApi.deleteTeacherAssignment(id),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ["admin", "teacher-assignments"] }),
  });

  function openCreate() {
    setEditing(null);
    setForm(EMPTY);
    save.reset();
    setFormOpen(true);
  }

  function openEdit(assignment: TeacherAssignment) {
    setEditing(assignment);
    setForm({
      teacherUserId: assignment.teacherUserId,
      classId: assignment.classId,
      subjectName: assignment.subjectName,
      classTeacher: assignment.classTeacher,
      active: assignment.active,
    });
    save.reset();
    setFormOpen(true);
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title="Teacher assignments"
        description="Assign a teacher to a class and subject. A class can have only one active class teacher."
        actions={<Button onClick={openCreate}>New assignment</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
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
        <Select
          value={teacherFilter}
          onChange={(event) => {
            setTeacherFilter(event.target.value === "" ? "" : Number(event.target.value));
            setPage(0);
          }}
          className="max-w-xs"
        >
          <option value="">All teachers</option>
          {teachers.data?.map((teacher) => (
            <option key={teacher.id} value={teacher.id}>
              {teacher.name}
            </option>
          ))}
        </Select>
      </div>

      {remove.isError ? <ErrorBanner error={remove.error} /> : null}
      {assignments.isError ? <ErrorBanner error={assignments.error} /> : null}

      <Card>
        {assignments.isPending ? (
          <div className="p-4">
            <Loading label="Loading assignments…" />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-2">Teacher</th>
                  <th className="px-4 py-2">Class</th>
                  <th className="px-4 py-2">Subject</th>
                  <th className="px-4 py-2">Flags</th>
                  <th className="px-4 py-2 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {assignments.data && assignments.data.content.length === 0 ? (
                  <EmptyRow colSpan={5} message="No assignments found." />
                ) : null}
                {assignments.data?.content.map((assignment) => (
                  <tr key={assignment.id} className="border-t border-slate-100">
                    <td className="px-4 py-2 font-medium">{assignment.teacherName}</td>
                    <td className="px-4 py-2 text-slate-600">
                      {assignment.className} {assignment.section}
                      <div className="text-xs text-slate-400">{assignment.academicYearName}</div>
                    </td>
                    <td className="px-4 py-2 text-slate-600">{assignment.subjectName}</td>
                    <td className="px-4 py-2">
                      <div className="flex flex-wrap gap-1">
                        {assignment.classTeacher ? <Badge tone="indigo">Class teacher</Badge> : null}
                        {assignment.active ? <Badge tone="green">Active</Badge> : <Badge tone="red">Inactive</Badge>}
                      </div>
                    </td>
                    <td className="px-4 py-2 text-right">
                      <div className="flex justify-end gap-2">
                        <Button variant="secondary" onClick={() => openEdit(assignment)}>
                          Edit
                        </Button>
                        <Button
                          variant="danger"
                          disabled={remove.isPending}
                          onClick={() => {
                            if (window.confirm("Delete this assignment?")) {
                              remove.mutate(assignment.id);
                            }
                          }}
                        >
                          Delete
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

      {assignments.data ? (
        <Pagination
          page={assignments.data.page}
          pageSize={PAGE_SIZE}
          totalPages={assignments.data.totalPages}
          totalElements={assignments.data.totalElements}
          onPageChange={setPage}
        />
      ) : null}

      <Modal
        open={formOpen}
        title={editing ? "Edit assignment" : "New assignment"}
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
        <Field label="Teacher">
          <Select
            value={form.teacherUserId ?? ""}
            onChange={(event) =>
              setForm({ ...form, teacherUserId: event.target.value === "" ? null : Number(event.target.value) })
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
        <Field label="Class">
          <Select
            value={form.classId ?? ""}
            onChange={(event) =>
              setForm({ ...form, classId: event.target.value === "" ? null : Number(event.target.value) })
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
        <Field label="Subject">
          <TextInput
            value={form.subjectName}
            onChange={(event) => setForm({ ...form, subjectName: event.target.value })}
            placeholder="Mathematics"
          />
        </Field>
        <CheckboxField
          label="Class teacher"
          checked={form.classTeacher}
          onChange={(classTeacher) => setForm({ ...form, classTeacher })}
        />
        <CheckboxField
          label="Active"
          checked={form.active}
          onChange={(active) => setForm({ ...form, active })}
        />
      </Modal>
    </section>
  );
}
