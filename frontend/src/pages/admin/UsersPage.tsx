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
import { useDebouncedValue } from "../../lib/hooks";
import type { AdminUser, Role } from "../../types/api";

const ALL_ROLES: Role[] = ["ADMIN", "TEACHER", "PARENT"];
const PAGE_SIZE = 20;

interface FormState {
  name: string;
  email: string;
  phone: string;
  active: boolean;
  roles: Role[];
}

function emptyForm(role: Role): FormState {
  return { name: "", email: "", phone: "", active: true, roles: [role] };
}

export function UsersPage({
  role,
  title,
  description,
}: {
  role: Role;
  title: string;
  description?: string;
}) {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [activeFilter, setActiveFilter] = useState<"" | "true" | "false">("");
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(query);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<AdminUser | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm(role));

  const users = useQuery({
    queryKey: ["admin", "users", role, debounced, activeFilter, page],
    queryFn: () =>
      adminApi.listUsers({
        role,
        query: debounced || undefined,
        active: activeFilter === "" ? undefined : activeFilter === "true",
        page,
      }),
    placeholderData: keepPreviousData,
  });

  const save = useMutation({
    mutationFn: async () => {
      if (editing) {
        await adminApi.updateUser(editing.id, {
          name: form.name,
          email: form.email,
          phone: form.phone,
          active: form.active,
        });
        return adminApi.replaceUserRoles(editing.id, form.roles);
      }
      return adminApi.createUser(form);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
      setFormOpen(false);
      setEditing(null);
    },
  });

  const remove = useMutation({
    mutationFn: (id: number) => adminApi.deleteUser(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
  });

  function openCreate() {
    setEditing(null);
    setForm(emptyForm(role));
    save.reset();
    setFormOpen(true);
  }

  function openEdit(user: AdminUser) {
    setEditing(user);
    setForm({
      name: user.name,
      email: user.email ?? "",
      phone: user.phone ?? "",
      active: user.active,
      roles: user.roles,
    });
    save.reset();
    setFormOpen(true);
  }

  function toggleRole(target: Role, checked: boolean) {
    setForm((current) => ({
      ...current,
      roles: checked
        ? [...new Set([...current.roles, target])]
        : current.roles.filter((value) => value !== target),
    }));
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title={title}
        description={description}
        actions={<Button onClick={openCreate}>New {title.toLowerCase().replace(/s$/, "")}</Button>}
      />

      <div className="flex flex-wrap items-center gap-2">
        <TextInput
          placeholder="Search by name, email or phone…"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setPage(0);
          }}
          className="max-w-xs"
        />
        <Select
          value={activeFilter}
          onChange={(event) => {
            setActiveFilter(event.target.value as "" | "true" | "false");
            setPage(0);
          }}
          className="max-w-[10rem]"
        >
          <option value="">All statuses</option>
          <option value="true">Active</option>
          <option value="false">Inactive</option>
        </Select>
      </div>

      {remove.isError ? <ErrorBanner error={remove.error} /> : null}
      {users.isError ? <ErrorBanner error={users.error} /> : null}

      <Card>
        {users.isPending ? (
          <div className="p-4">
            <Loading label={`Loading ${title.toLowerCase()}…`} />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-2">Name</th>
                  <th className="px-4 py-2">Contact</th>
                  <th className="px-4 py-2">Roles</th>
                  <th className="px-4 py-2">Status</th>
                  <th className="px-4 py-2 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.data && users.data.content.length === 0 ? (
                  <EmptyRow colSpan={5} message={`No ${title.toLowerCase()} found.`} />
                ) : null}
                {users.data?.content.map((user) => (
                  <tr key={user.id} className="border-t border-slate-100">
                    <td className="px-4 py-2 font-medium">{user.name}</td>
                    <td className="px-4 py-2 text-slate-600">
                      {user.phone ?? "—"}
                      {user.email ? <div className="text-xs text-slate-400">{user.email}</div> : null}
                    </td>
                    <td className="px-4 py-2">
                      <div className="flex flex-wrap gap-1">
                        {user.roles.map((value) => (
                          <Badge key={value} tone="indigo">
                            {value}
                          </Badge>
                        ))}
                      </div>
                    </td>
                    <td className="px-4 py-2">
                      {user.active ? <Badge tone="green">Active</Badge> : <Badge tone="red">Inactive</Badge>}
                    </td>
                    <td className="px-4 py-2 text-right">
                      <div className="flex justify-end gap-2">
                        <Button variant="secondary" onClick={() => openEdit(user)}>
                          Edit
                        </Button>
                        <Button
                          variant="danger"
                          disabled={remove.isPending}
                          onClick={() => {
                            if (window.confirm(`Delete ${user.name}?`)) {
                              remove.mutate(user.id);
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

      {users.data ? (
        <Pagination
          page={users.data.page}
          pageSize={PAGE_SIZE}
          totalPages={users.data.totalPages}
          totalElements={users.data.totalElements}
          onPageChange={setPage}
        />
      ) : null}

      <Modal
        open={formOpen}
        title={editing ? `Edit ${editing.name}` : `New ${role.toLowerCase()}`}
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
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
        </Field>
        <Field label="Phone" hint="Needed for phone-OTP sign-in. At least one of phone or email is required.">
          <TextInput
            value={form.phone}
            onChange={(event) => setForm({ ...form, phone: event.target.value })}
            placeholder="+919876543210"
          />
        </Field>
        <Field label="Email">
          <TextInput
            type="email"
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
          />
        </Field>
        <Field label="Roles" hint="A user needs at least one role so they can sign in.">
          <div className="space-y-2">
            {ALL_ROLES.map((value) => (
              <CheckboxField
                key={value}
                label={value}
                checked={form.roles.includes(value)}
                onChange={(checked) => toggleRole(value, checked)}
              />
            ))}
          </div>
        </Field>
        <CheckboxField
          label="Account is active"
          checked={form.active}
          onChange={(active) => setForm({ ...form, active })}
        />
      </Modal>
    </section>
  );
}
