import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import {
  Badge,
  Button,
  Card,
  EmptyRow,
  ErrorBanner,
  Loading,
  PageHeader,
  TextArea,
} from "../../components/ui";
import * as adminApi from "../../lib/adminApi";
import type { StudentImportOutcome, StudentImportReport } from "../../types/api";

const TEMPLATE = [
  "admission_no,full_name,dob,admission_date,status,class_name,class_section,roll_no,parent_name,parent_phone,parent_email,relationship",
  "ADM-2025-001,Aarav Sharma,2015-04-01,2020-06-01,ACTIVE,Grade 1,A,1,Sunita Sharma,+919876543210,sunita@example.com,MOTHER",
].join("\n");

const OUTCOME_TONES: Record<StudentImportOutcome, "green" | "indigo" | "amber" | "red"> = {
  CREATE_STUDENT: "green",
  LINK_PARENT: "indigo",
  DUPLICATE: "amber",
  ERROR: "red",
};

const OUTCOME_LABELS: Record<StudentImportOutcome, string> = {
  CREATE_STUDENT: "Create",
  LINK_PARENT: "Link parent",
  DUPLICATE: "Duplicate",
  ERROR: "Error",
};

export function StudentImportPage() {
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [csv, setCsv] = useState("");
  const [report, setReport] = useState<StudentImportReport | null>(null);

  const preview = useMutation({
    mutationFn: () => adminApi.importStudents(csv, true),
    onSuccess: setReport,
  });

  const commit = useMutation({
    mutationFn: () => adminApi.importStudents(csv, false),
    onSuccess: (result) => {
      setReport(result);
      void queryClient.invalidateQueries({ queryKey: ["admin", "students"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "parent-children"] });
    },
  });

  function loadFile(file: File | undefined) {
    if (!file) {
      return;
    }
    void file.text().then((text) => {
      setCsv(text);
      setReport(null);
    });
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title="Import students & parents"
        description="Paste or upload a CSV, preview the row-by-row result, then commit. Nothing is written until you commit, and existing students are never duplicated."
        actions={
          <Button variant="secondary" onClick={() => setCsv(TEMPLATE)}>
            Insert sample
          </Button>
        }
      />

      <Card className="space-y-3 p-4">
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="secondary" onClick={() => fileInput.current?.click()}>
            Choose CSV file…
          </Button>
          <input
            ref={fileInput}
            type="file"
            accept=".csv,text/csv"
            className="hidden"
            onChange={(event) => {
              loadFile(event.target.files?.[0]);
              event.target.value = "";
            }}
          />
          <span className="text-xs text-slate-500">
            Columns: admission_no, full_name, dob, admission_date, status, class_name, class_section,
            roll_no, parent_name, parent_phone, parent_email, relationship
          </span>
        </div>

        <TextArea
          rows={10}
          value={csv}
          onChange={(event) => {
            setCsv(event.target.value);
            setReport(null);
          }}
          placeholder={TEMPLATE}
        />

        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            disabled={preview.isPending || csv.trim() === ""}
            onClick={() => preview.mutate()}
          >
            {preview.isPending ? "Checking…" : "Preview"}
          </Button>
          <Button
            disabled={commit.isPending || !report || !report.canCommit}
            onClick={() => commit.mutate()}
          >
            {commit.isPending ? "Importing…" : "Commit import"}
          </Button>
        </div>

        {preview.isError ? <ErrorBanner error={preview.error} /> : null}
        {commit.isError ? <ErrorBanner error={commit.error} /> : null}
      </Card>

      {report ? (
        <Card className="space-y-4 p-4">
          <div className="flex flex-wrap items-center gap-3">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Result</h2>
            {report.committed ? (
              <Badge tone="green">Committed</Badge>
            ) : (
              <Badge tone="amber">Preview only — nothing saved</Badge>
            )}
          </div>

          <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm sm:grid-cols-3 lg:grid-cols-6">
            {[
              ["Rows", report.totalRows],
              ["Students to create", report.studentsToCreate],
              ["Parents to create", report.parentsToCreate],
              ["Links to create", report.linksToCreate],
              ["Duplicates", report.duplicateLinks],
              ["Errors", report.errorCount],
            ].map(([label, value]) => (
              <div key={label as string}>
                <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
                <dd className="text-lg font-semibold">{value}</dd>
              </div>
            ))}
          </dl>

          {!report.committed && report.canCommit ? (
            <p className="text-sm text-emerald-700">
              No errors found. You can commit this import safely.
            </p>
          ) : null}
          {!report.canCommit ? (
            <p className="text-sm text-red-700">
              Fix the rows marked “Error” before committing. Committing is blocked while any row has an
              error.
            </p>
          ) : null}

          {commit.isPending ? <Loading label="Importing…" /> : null}

          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-3 py-2">Row</th>
                  <th className="px-3 py-2">Outcome</th>
                  <th className="px-3 py-2">Student</th>
                  <th className="px-3 py-2">Admission no</th>
                  <th className="px-3 py-2">Parent</th>
                  <th className="px-3 py-2">Messages</th>
                </tr>
              </thead>
              <tbody>
                {report.rows.length === 0 ? <EmptyRow colSpan={6} message="No rows." /> : null}
                {report.rows.map((row) => (
                  <tr key={row.rowNumber} className="border-t border-slate-100 align-top">
                    <td className="px-3 py-2 text-slate-500">{row.rowNumber}</td>
                    <td className="px-3 py-2">
                      <Badge tone={OUTCOME_TONES[row.outcome]}>{OUTCOME_LABELS[row.outcome]}</Badge>
                    </td>
                    <td className="px-3 py-2 font-medium">{row.studentName || "—"}</td>
                    <td className="px-3 py-2 text-slate-600">{row.admissionNo || "—"}</td>
                    <td className="px-3 py-2 text-slate-600">{row.parentName || "—"}</td>
                    <td className="px-3 py-2 text-slate-600">
                      {row.messages.length === 0 ? (
                        "—"
                      ) : (
                        <ul className="list-disc space-y-0.5 pl-4 text-red-700">
                          {row.messages.map((message) => (
                            <li key={message}>{message}</li>
                          ))}
                        </ul>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      ) : null}
    </section>
  );
}
