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
  TextInput,
} from "../../components/ui";
import * as adminApi from "../../lib/adminApi";
import { useDebouncedValue } from "../../lib/hooks";
import type { AcademicYear, AcademicYearInput } from "../../types/api";

const EMPTY: AcademicYearInput = { name: "", startDate: "", endDate: "", current: false };
const PAGE_SIZE = 20;

export function AcademicYearsPage() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(query);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<AcademicYear | null>(null);
  const [form, setForm] = useState<AcademicYearInput>(EMPTY);

  const years = useQuery({
    queryKey: ["admin", "academic-years", debounced, page],
    queryFn: () => adminApi.listAcademicYears({ query: debounced || undefined, page }),
    placeholderData: keepPreviousData,
  });

  const save = useMutation({
    mutationFn: () =>
      editing ? adminApi.updateAcademicYear(editing.id, form) : adminApi.createAcademicYear(form),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "academic-years"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "academic-year-options"] });
      setFormOpen(false);
      setEditing(null);
    },
  });

  const remove = useMutation({
    mutationFn: (id: number) => adminApi.deleteAcademicYear(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["admin", "academic-years"] }),
  });

  function openCreate() {
    setEditing(null);
    setForm(EMPTY);
    save.reset();
    setFormOpen(true);
  }

  function openEdit(year: AcademicYear) {
    setEditing(year);
    setForm({ name: year.name, startDate: year.startDate, endDate: year.endDate, current: year.current });
    save.reset();
    setFormOpen(true);
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title="Academic years"
        description="Each year groups the classes that run within it. Exactly one year can be current."
        actions={<Button onClick={openCreate}>New academic year</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <TextInput
          placeholder="Search by name…"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setPage(0);
          }}
          className="max-w-xs"
        />
      </div>

      {remove.isError ? <ErrorBanner error={remove.error} /> : null}
      {years.isError ? <ErrorBanner error={years.error} /> : null}

      <Card>
        {years.isPending ? (
          <div className="p-4">
            <Loading label="Loading academic years…" />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-2">Name</th>
                  <th className="px-4 py-2">Starts</th>
                  <th className="px-4 py-2">Ends</th>
                  <th className="px-4 py-2">Status</th>
                  <th className="px-4 py-2 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {years.data && years.data.content.length === 0 ? (
                  <EmptyRow colSpan={5} message="No academic years yet." />
                ) : null}
                {years.data?.content.map((year) => (
                  <tr key={year.id} className="border-t border-slate-100">
                    <td className="px-4 py-2 font-medium">{year.name}</td>
                    <td className="px-4 py-2 text-slate-600">{year.startDate}</td>
                    <td className="px-4 py-2 text-slate-600">{year.endDate}</td>
                    <td className="px-4 py-2">
                      {year.current ? <Badge tone="green">Current</Badge> : <Badge>Past</Badge>}
                    </td>
                    <td className="px-4 py-2 text-right">
                      <div className="flex justify-end gap-2">
                        <Button variant="secondary" onClick={() => openEdit(year)}>
                          Edit
                        </Button>
                        <Button
                          variant="danger"
                          disabled={remove.isPending}
                          onClick={() => {
                            if (window.confirm(`Delete ${year.name}? This cannot be undone.`)) {
                              remove.mutate(year.id);
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

      {years.data ? (
        <Pagination
          page={years.data.page}
          pageSize={PAGE_SIZE}
          totalPages={years.data.totalPages}
          totalElements={years.data.totalElements}
          onPageChange={setPage}
        />
      ) : null}

      <Modal
        open={formOpen}
        title={editing ? "Edit academic year" : "New academic year"}
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
        <Field label="Name">
          <TextInput
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
            placeholder="2025-2026"
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Start date">
            <TextInput
              type="date"
              value={form.startDate}
              onChange={(event) => setForm({ ...form, startDate: event.target.value })}
            />
          </Field>
          <Field label="End date">
            <TextInput
              type="date"
              value={form.endDate}
              onChange={(event) => setForm({ ...form, endDate: event.target.value })}
            />
          </Field>
        </div>
        <CheckboxField
          label="Make this the current academic year"
          checked={form.current}
          onChange={(current) => setForm({ ...form, current })}
        />
      </Modal>
    </section>
  );
}
