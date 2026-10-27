import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
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
import { useDebouncedValue } from "../../lib/hooks";
import type { Student } from "../../types/api";

export function ParentLinksPage() {
  const queryClient = useQueryClient();
  const [parentUserId, setParentUserId] = useState<number | "">("");
  const [studentQuery, setStudentQuery] = useState("");
  const [selectedStudent, setSelectedStudent] = useState<Student | null>(null);
  const [relationship, setRelationship] = useState("");
  const debounced = useDebouncedValue(studentQuery);

  const parents = useQuery({
    queryKey: ["admin", "user-options", "PARENT"],
    queryFn: () => adminApi.userOptions("PARENT"),
  });

  const children = useQuery({
    queryKey: ["admin", "parent-children", parentUserId],
    queryFn: () => adminApi.parentChildren(parentUserId as number),
    enabled: parentUserId !== "",
  });

  const studentResults = useQuery({
    queryKey: ["admin", "student-search", debounced],
    queryFn: () => adminApi.listStudents({ query: debounced, page: 0, size: 8 }),
    enabled: debounced.trim().length >= 2,
  });

  const link = useMutation({
    mutationFn: () =>
      adminApi.linkParentStudent({
        parentUserId: parentUserId === "" ? null : parentUserId,
        studentId: selectedStudent ? selectedStudent.id : null,
        relationship,
      }),
    onSuccess: () => {
      setSelectedStudent(null);
      setRelationship("");
      setStudentQuery("");
      void queryClient.invalidateQueries({ queryKey: ["admin", "parent-children", parentUserId] });
    },
  });

  const unlink = useMutation({
    mutationFn: (studentId: number) => adminApi.unlinkParentStudent(parentUserId as number, studentId),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ["admin", "parent-children", parentUserId] }),
  });

  return (
    <section className="space-y-6">
      <PageHeader
        title="Parent links"
        description="Connect parents to their children. A parent can be linked to more than one student."
      />

      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={parentUserId}
          onChange={(event) => {
            setParentUserId(event.target.value === "" ? "" : Number(event.target.value));
            setSelectedStudent(null);
            setStudentQuery("");
          }}
          className="max-w-sm"
        >
          <option value="">Select a parent</option>
          {parents.data?.map((parent) => (
            <option key={parent.id} value={parent.id}>
              {parent.name}
              {parent.phone ? ` · ${parent.phone}` : ""}
            </option>
          ))}
        </Select>
      </div>

      {parentUserId === "" ? (
        <p className="text-sm text-slate-500">Choose a parent to see and manage their children.</p>
      ) : (
        <Card className="p-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Children</h2>
          {children.isPending ? (
            <div className="mt-3">
              <Loading label="Loading children…" />
            </div>
          ) : (
            <div className="mt-3 overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="py-2">Student</th>
                    <th className="py-2">Admission no</th>
                    <th className="py-2">Class</th>
                    <th className="py-2">Relationship</th>
                    <th className="py-2 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {children.data && children.data.length === 0 ? (
                    <EmptyRow colSpan={5} message="No children linked yet." />
                  ) : null}
                  {children.data?.map((child) => (
                    <tr key={child.studentId} className="border-t border-slate-100">
                      <td className="py-2 font-medium">
                        <Link
                          to={`/admin/students/${child.studentId}`}
                          className="text-indigo-600 hover:underline"
                        >
                          {child.fullName ?? "—"}
                        </Link>
                      </td>
                      <td className="py-2 text-slate-600">{child.admissionNo ?? "—"}</td>
                      <td className="py-2 text-slate-600">
                        {child.className ? `${child.className} ${child.section}` : "—"}
                      </td>
                      <td className="py-2 text-slate-600">{child.relationship ?? "—"}</td>
                      <td className="py-2 text-right">
                        <Button
                          variant="danger"
                          disabled={unlink.isPending}
                          onClick={() => unlink.mutate(child.studentId)}
                        >
                          Unlink
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {children.isError ? <div className="mt-2"><ErrorBanner error={children.error} /></div> : null}
          {unlink.isError ? <div className="mt-2"><ErrorBanner error={unlink.error} /></div> : null}

          <div className="mt-6 space-y-3 border-t border-slate-100 pt-4">
            <h3 className="text-sm font-semibold text-slate-700">Link another child</h3>
            <Field label="Find a student" hint="Type at least two characters of the name or admission number.">
              <TextInput
                value={studentQuery}
                onChange={(event) => {
                  setStudentQuery(event.target.value);
                  setSelectedStudent(null);
                }}
                placeholder="Search students…"
              />
            </Field>
            {selectedStudent ? (
              <p className="text-sm text-slate-700">
                Selected: <span className="font-medium">{selectedStudent.fullName}</span> (
                {selectedStudent.admissionNo})
              </p>
            ) : null}
            {!selectedStudent && studentResults.data && studentResults.data.content.length > 0 ? (
              <ul className="divide-y divide-slate-100 rounded-md border border-slate-200">
                {studentResults.data.content.map((student) => (
                  <li key={student.id}>
                    <button
                      type="button"
                      className="flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-slate-50"
                      onClick={() => {
                        setSelectedStudent(student);
                        setStudentQuery("");
                      }}
                    >
                      <span className="font-medium">{student.fullName}</span>
                      <span className="text-xs text-slate-500">
                        {student.admissionNo}
                        {student.currentClassName ? ` · ${student.currentClassName} ${student.currentSection}` : ""}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
            <div className="grid gap-3 sm:grid-cols-3">
              <Field label="Relationship">
                <TextInput
                  value={relationship}
                  onChange={(event) => setRelationship(event.target.value)}
                  placeholder="MOTHER"
                />
              </Field>
              <div className="flex items-end">
                <Button
                  disabled={link.isPending || !selectedStudent}
                  onClick={() => link.mutate()}
                >
                  {link.isPending ? "Linking…" : "Link student"}
                </Button>
              </div>
            </div>
            {link.isError ? <ErrorBanner error={link.error} /> : null}
          </div>
        </Card>
      )}
    </section>
  );
}
