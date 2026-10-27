import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
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
import type { SchoolClass, SchoolClassInput } from "../../types/api";

const EMPTY: SchoolClassInput = { name: "", section: "", academicYearId: null };
const PAGE_SIZE = 20;

export function ClassesPage() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [yearFilter, setYearFilter] = useState<number | "">("");
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(query);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<SchoolClass | null>(null);
  const [form, setForm] = useState<SchoolClassInput>(EMPTY);

  const years = useQuery({
    queryKey: ["admin", "academic-year-options"],
    queryFn: () => adminApi.academicYearOptions(),
  });

  const classes = useQuery({
    queryKey: ["admin", "classes", debounced, yearFilter, page],
    queryFn: () =>
      adminApi.listClasses({
        query: debounced || undefined,
        academicYearId: yearFilter === "" ? undefined : yearFilter,
        page,
      }),
    placeholderData: keepPreviousData,
  });

  const save = useMutation({
    mutationFn: () => (editing ? adminApi.updateClass(editing.id, form) : adminApi.createClass(form)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "classes"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "class-options"] });
      setFormOpen(false);
      setEditing(null);
    },
  });

  const remove = useMutation({
    mutationFn: (id: number) => adminApi.deleteClass(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["admin", "classes"] }),
  });

  function openCreate() {
    setEditing(null);
    setForm({ ...EMPTY, academicYearId: years.data?.find((year) => year.current)?.id ?? years.data?.[0]?.id ?? null });
    save.reset();
    setFormOpen(true);
  }

  function openEdit(schoolClass: SchoolClass) {
    setEditing(schoolClass);
    setForm({
      name: schoolClass.name,
      section: schoolClass.section,
      academicYearId: schoolClass.academicYearId,
    });
    save.reset();
    setFormOpen(true);
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title="Classes & sections"
        description="Classes belong to an academic year and are identified by name and section."
        actions={<Button onClick={openCreate}>New class</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <TextInput
          placeholder="Search by name or section…"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setPage(0);
          }}
          className="max-w-xs"
        />
        <Select
          value={yearFilter}
          onChange={(event) => {
            setYearFilter(event.target.value === "" ? "" : Number(event.target.value));
            setPage(0);
          }}
          className="max-w-xs"
        >
          <option value="">All academic years</option>
          {years.data?.map((year) => (
            <option key={year.id} value={year.id}>
              {year.name}
              {year.current ? " (current)" : ""}
            </option>
          ))}
        </Select>
      </div>

      {remove.isError ? <ErrorBanner error={remove.error} /> : null}
      {classes.isError ? <ErrorBanner error={classes.error} /> : null}

      <Card>
        {classes.isPending ? (
          <div className="p-4">
            <Loading label="Loading classes…" />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-2">Name</th>
                  <th className="px-4 py-2">Section</th>
                  <th className="px-4 py-2">Academic year</th>
                  <th className="px-4 py-2 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {classes.data && classes.data.content.length === 0 ? (
                  <EmptyRow colSpan={4} message="No classes found." />
                ) : null}
                {classes.data?.content.map((schoolClass) => (
                  <tr key={schoolClass.id} className="border-t border-slate-100">
                    <td className="px-4 py-2 font-medium">{schoolClass.name}</td>
                    <td className="px-4 py-2 text-slate-600">{schoolClass.section}</td>
                    <td className="px-4 py-2 text-slate-600">{schoolClass.academicYearName}</td>
                    <td className="px-4 py-2 text-right">
                      <div className="flex justify-end gap-2">
                        <Button variant="secondary" onClick={() => openEdit(schoolClass)}>
                          Edit
                        </Button>
                        <Button
                          variant="danger"
                          disabled={remove.isPending}
                          onClick={() => {
                            if (window.confirm(`Delete ${schoolClass.name} ${schoolClass.section}?`)) {
                              remove.mutate(schoolClass.id);
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

      {classes.data ? (
        <Pagination
          page={classes.data.page}
          pageSize={PAGE_SIZE}
          totalPages={classes.data.totalPages}
          totalElements={classes.data.totalElements}
          onPageChange={setPage}
        />
      ) : null}

      <Modal
        open={formOpen}
        title={editing ? "Edit class" : "New class"}
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
        <Field label="Academic year">
          <Select
            value={form.academicYearId ?? ""}
            onChange={(event) =>
              setForm({ ...form, academicYearId: event.target.value === "" ? null : Number(event.target.value) })
            }
          >
            <option value="">Select an academic year</option>
            {years.data?.map((year) => (
              <option key={year.id} value={year.id}>
                {year.name}
              </option>
            ))}
          </Select>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Name">
            <TextInput
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              placeholder="Grade 5"
            />
          </Field>
          <Field label="Section">
            <TextInput
              value={form.section}
              onChange={(event) => setForm({ ...form, section: event.target.value })}
              placeholder="A"
            />
          </Field>
        </div>
      </Modal>
    </section>
  );
}
