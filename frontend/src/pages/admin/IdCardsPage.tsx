import { useMutation, useQuery } from "@tanstack/react-query";
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
} from "../../components/ui";
import * as adminApi from "../../lib/adminApi";
import { saveBlob } from "../../lib/download";
import type { Student } from "../../types/api";

export function IdCardsPage() {
  const [classId, setClassId] = useState<number | "">("");

  const classes = useQuery({
    queryKey: ["admin", "class-options", "all"],
    queryFn: () => adminApi.classOptions(),
  });

  const roster = useQuery({
    queryKey: ["admin", "id-cards", "roster", classId],
    queryFn: () => adminApi.listStudents({ classId: Number(classId), size: 200 }),
    enabled: classId !== "",
  });

  const selectedClass = classes.data?.find((schoolClass) => schoolClass.id === classId) ?? null;

  const classSheet = useMutation({
    mutationFn: () => adminApi.downloadClassIdCards(Number(classId)),
    onSuccess: (blob) => {
      const label = selectedClass ? `${selectedClass.name}-${selectedClass.section}` : "class";
      saveBlob(blob, `${label}-id-cards.pdf`);
    },
  });

  const studentCard = useMutation({
    mutationFn: (student: Student) => adminApi.downloadStudentIdCard(student.id),
    onSuccess: (blob, student) => saveBlob(blob, `${student.admissionNo}-id-card.pdf`),
  });

  return (
    <section className="space-y-6">
      <PageHeader
        title="ID cards"
        description="Generate printable ID card sheets. Each card shows the student's photo, name and class, plus a QR code that encodes only the opaque QR token — never personal data."
      />

      <Card className="space-y-4 p-4">
        <div className="grid gap-3 sm:grid-cols-[minmax(0,22rem)_auto] sm:items-end">
          <Field label="Class" hint="Print a whole class at once, or download a single card below.">
            <Select
              value={classId}
              onChange={(event) => {
                setClassId(event.target.value === "" ? "" : Number(event.target.value));
                classSheet.reset();
                studentCard.reset();
              }}
            >
              <option value="">Select a class</option>
              {classes.data?.map((schoolClass) => (
                <option key={schoolClass.id} value={schoolClass.id}>
                  {schoolClass.name} {schoolClass.section} · {schoolClass.academicYearName}
                </option>
              ))}
            </Select>
          </Field>
          <div>
            <Button
              disabled={classId === "" || classSheet.isPending}
              onClick={() => classSheet.mutate()}
            >
              {classSheet.isPending ? "Generating…" : "Print whole class"}
            </Button>
          </div>
        </div>
        {classSheet.isError ? <ErrorBanner error={classSheet.error} /> : null}
        {studentCard.isError ? <ErrorBanner error={studentCard.error} /> : null}
      </Card>

      {classId === "" ? (
        <Card className="p-4">
          <p className="text-sm text-slate-500">
            Select a class to preview its students and download their ID cards.
          </p>
        </Card>
      ) : (
        <Card>
          {roster.isPending ? (
            <div className="p-4">
              <Loading label="Loading class roster…" />
            </div>
          ) : roster.isError ? (
            <div className="p-4">
              <ErrorBanner error={roster.error} />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-4 py-2">Roll</th>
                    <th className="px-4 py-2">Name</th>
                    <th className="px-4 py-2">Admission no</th>
                    <th className="px-4 py-2 text-right">ID card</th>
                  </tr>
                </thead>
                <tbody>
                  {roster.data && roster.data.content.length === 0 ? (
                    <EmptyRow colSpan={4} message="No students are enrolled in this class." />
                  ) : null}
                  {roster.data?.content.map((student) => (
                    <tr key={student.id} className="border-t border-slate-100">
                      <td className="px-4 py-2 text-slate-600">{student.rollNo ?? "—"}</td>
                      <td className="px-4 py-2 font-medium">
                        <Link
                          to={`/admin/students/${student.id}`}
                          className="text-indigo-600 hover:underline"
                        >
                          {student.fullName}
                        </Link>
                      </td>
                      <td className="px-4 py-2 text-slate-600">{student.admissionNo}</td>
                      <td className="px-4 py-2 text-right">
                        <Button
                          variant="secondary"
                          disabled={studentCard.isPending}
                          onClick={() => studentCard.mutate(student)}
                        >
                          Download
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}
    </section>
  );
}
