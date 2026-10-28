import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Badge,
  Button,
  Card,
  ErrorBanner,
  Field,
  Loading,
  Modal,
  PageHeader,
  Pagination,
  Select,
  TextArea,
  TextInput,
} from "../components/ui";
import { useAuth } from "../auth/useAuth";
import * as noticesApi from "../lib/noticesApi";
import type { Notice, NoticeScope } from "../types/api";

const PAGE_SIZE = 20;

interface FormState {
  scope: NoticeScope;
  classId: number | "";
  title: string;
  body: string;
  expiresAt: string;
  senderLabel: string;
}

function toInputValue(iso: string | null | undefined): string {
  if (!iso) {
    return "";
  }
  const date = new Date(iso);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function NoticesPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isAdmin = user?.roles.includes("ADMIN") ?? false;

  const [page, setPage] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Notice | null>(null);
  const [form, setForm] = useState<FormState>({
    scope: "GLOBAL",
    classId: "",
    title: "",
    body: "",
    expiresAt: "",
    senderLabel: "",
  });
  const [file, setFile] = useState<File | null>(null);
  const [fileInputKey, setFileInputKey] = useState(0);
  const [removeAttachment, setRemoveAttachment] = useState(false);
  const [holidayTarget, setHolidayTarget] = useState<Notice | null>(null);
  const [holidayDate, setHolidayDate] = useState("");
  const [holidayDescription, setHolidayDescription] = useState("");
  const [toast, setToast] = useState<string | null>(null);

  const notices = useQuery({
    queryKey: ["notices", "list", page],
    queryFn: () => noticesApi.listNotices(page, PAGE_SIZE),
  });

  const classes = useQuery({
    queryKey: ["notices", "assignable-classes"],
    queryFn: noticesApi.assignableClasses,
  });

  const save = useMutation({
    mutationFn: async () => {
      const expiresAt = form.expiresAt ? new Date(form.expiresAt).toISOString() : null;
      if (editing) {
        await noticesApi.updateNotice(editing.id, {
          title: form.title,
          body: form.body,
          expiresAt,
          clearExpiry: Boolean(editing.expiresAt) && form.expiresAt === "",
          removeAttachment: removeAttachment && !file,
        });
        if (file) {
          await noticesApi.uploadNoticeAttachment(editing.id, file);
        }
        return;
      }
      const created = await noticesApi.createNotice({
        title: form.title,
        body: form.body,
        scope: form.scope,
        classId: form.scope === "CLASS" ? (form.classId === "" ? null : Number(form.classId)) : null,
        expiresAt,
        senderLabel: isAdmin ? form.senderLabel.trim() || null : null,
      });
      if (file) {
        await noticesApi.uploadNoticeAttachment(created.id, file);
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["notices"] });
      setFormOpen(false);
      setEditing(null);
      setFile(null);
    },
  });

  const remove = useMutation({
    mutationFn: (id: number) => noticesApi.deleteNotice(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["notices"] }),
  });

  const holiday = useMutation({
    mutationFn: () =>
      noticesApi.createHolidayFromNotice(holidayTarget!.id, {
        date: holidayDate,
        description: holidayDescription.trim() || undefined,
      }),
    onSuccess: () => {
      setHolidayTarget(null);
      setHolidayDate("");
      setHolidayDescription("");
      setToast("Holiday added to the school calendar.");
    },
  });

  function openCreate() {
    setEditing(null);
    setForm({
      scope: isAdmin ? "GLOBAL" : "CLASS",
      classId: classes.data && classes.data.length > 0 ? classes.data[0].classId : "",
      title: "",
      body: "",
      expiresAt: "",
      senderLabel: "",
    });
    setFile(null);
    setFileInputKey((value) => value + 1);
    setRemoveAttachment(false);
    save.reset();
    setFormOpen(true);
  }

  function openEdit(notice: Notice) {
    setEditing(notice);
    setForm({
      scope: notice.scope,
      classId: notice.classId ?? "",
      title: notice.title,
      body: notice.body,
      expiresAt: toInputValue(notice.expiresAt),
      senderLabel: notice.senderLabel,
    });
    setFile(null);
    setFileInputKey((value) => value + 1);
    setRemoveAttachment(false);
    save.reset();
    setFormOpen(true);
  }

  async function copyForWhatsApp(notice: Notice) {
    try {
      await navigator.clipboard.writeText(noticesApi.formatNoticeForWhatsApp(notice));
      setToast("Copied — ready to paste into WhatsApp.");
    } catch {
      setToast("Could not copy to the clipboard.");
    }
  }

  function canManage(notice: Notice) {
    return isAdmin || notice.senderUserId === user?.id;
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title="Notices"
        description={
          isAdmin
            ? "Admins post school-wide notices; you can also post to a single class."
            : "Post notices to the classes you are assigned to."
        }
        actions={<Button onClick={openCreate}>New notice</Button>}
      />

      {toast ? (
        <p className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
          {toast}
        </p>
      ) : null}

      {notices.isError ? <ErrorBanner error={notices.error} /> : null}
      {notices.isPending ? <Loading label="Loading notices…" /> : null}

      {notices.data ? (
        <div className="space-y-3">
          {notices.data.content.length === 0 ? (
            <Card className="p-4">
              <p className="text-sm text-slate-600">No notices yet.</p>
            </Card>
          ) : (
            notices.data.content.map((notice) => (
              <Card key={notice.id} className="space-y-3 p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="space-y-1">
                    <h2 className="text-base font-semibold">{notice.title}</h2>
                    <p className="text-xs text-slate-500">
                      {notice.senderLabel} · {new Date(notice.createdAt).toLocaleString()}
                    </p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge tone={notice.scope === "GLOBAL" ? "indigo" : "slate"}>
                      {notice.scope === "GLOBAL" ? "Global" : notice.classLabel ?? "Class"}
                    </Badge>
                    {notice.edited ? <Badge tone="amber">edited</Badge> : null}
                    {notice.expired ? <Badge tone="red">expired</Badge> : null}
                  </div>
                </div>

                <p className="whitespace-pre-wrap text-sm text-slate-700">{notice.body}</p>

                {notice.attachment ? (
                  <a
                    href={noticesApi.noticeAttachmentUrl(notice.attachment.url)}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 text-sm text-indigo-600 hover:underline"
                  >
                    📎 {notice.attachment.fileName}
                  </a>
                ) : null}

                <div className="flex flex-wrap gap-2 border-t border-slate-100 pt-3">
                  <Button variant="secondary" onClick={() => void copyForWhatsApp(notice)}>
                    Copy for WhatsApp
                  </Button>
                  {canManage(notice) ? (
                    <>
                      <Button variant="secondary" onClick={() => openEdit(notice)}>
                        Edit
                      </Button>
                      <Button
                        variant="danger"
                        onClick={() => {
                          if (window.confirm(`Delete “${notice.title}”?`)) {
                            remove.mutate(notice.id);
                          }
                        }}
                      >
                        Delete
                      </Button>
                    </>
                  ) : null}
                  {isAdmin && notice.scope === "GLOBAL" ? (
                    <Button
                      variant="ghost"
                      onClick={() => {
                        setHolidayTarget(notice);
                        setHolidayDate("");
                        setHolidayDescription(notice.title);
                        holiday.reset();
                      }}
                    >
                      Add holiday
                    </Button>
                  ) : null}
                </div>
              </Card>
            ))
          )}

          <Pagination
            page={notices.data.page}
            pageSize={notices.data.size}
            totalPages={notices.data.totalPages}
            totalElements={notices.data.totalElements}
            onPageChange={setPage}
          />
        </div>
      ) : null}

      <Modal
        open={formOpen}
        title={editing ? "Edit notice" : "New notice"}
        onClose={() => setFormOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setFormOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={save.isPending || form.title.trim() === "" || form.body.trim() === ""}
              onClick={() => save.mutate()}
            >
              {save.isPending ? "Saving…" : editing ? "Save changes" : "Post notice"}
            </Button>
          </>
        }
      >
        {isAdmin && !editing ? (
          <Field label="Scope">
            <Select
              value={form.scope}
              onChange={(event) => setForm({ ...form, scope: event.target.value as NoticeScope })}
            >
              <option value="GLOBAL">Global (whole school)</option>
              <option value="CLASS">A single class</option>
            </Select>
          </Field>
        ) : null}

        {form.scope === "CLASS" && !editing ? (
          <Field label="Class">
            <Select
              value={form.classId}
              onChange={(event) =>
                setForm({ ...form, classId: event.target.value === "" ? "" : Number(event.target.value) })
              }
            >
              <option value="">Select a class</option>
              {classes.data?.map((option) => (
                <option key={option.classId} value={option.classId}>
                  {option.className} {option.section} · {option.senderLabel}
                </option>
              ))}
            </Select>
          </Field>
        ) : null}

        {isAdmin && !editing ? (
          <Field label="Sender label" hint="Optional — shown to parents. Defaults to your name.">
            <TextInput
              value={form.senderLabel}
              onChange={(event) => setForm({ ...form, senderLabel: event.target.value })}
              placeholder="Principal"
            />
          </Field>
        ) : null}

        <Field label="Title">
          <TextInput
            value={form.title}
            onChange={(event) => setForm({ ...form, title: event.target.value })}
            placeholder="Sports day"
          />
        </Field>

        <Field label="Body">
          <TextArea
            rows={5}
            value={form.body}
            onChange={(event) => setForm({ ...form, body: event.target.value })}
            placeholder="Details…"
          />
        </Field>

        <Field label="Expires" hint="Optional — the notice is hidden from parents after this time.">
          <TextInput
            type="datetime-local"
            value={form.expiresAt}
            onChange={(event) => setForm({ ...form, expiresAt: event.target.value })}
          />
        </Field>

        <Field label="Attachment" hint="Optional. PDF, image or text file within the size limit.">
          <input
            key={fileInputKey}
            type="file"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            className="block w-full text-sm text-slate-600"
          />
        </Field>

        {editing?.attachment ? (
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={removeAttachment}
              onChange={(event) => setRemoveAttachment(event.target.checked)}
              className="h-4 w-4 rounded border-slate-300"
            />
            Remove the current attachment ({editing.attachment.fileName})
          </label>
        ) : null}

        {save.isError ? <ErrorBanner error={save.error} /> : null}
      </Modal>

      <Modal
        open={holidayTarget !== null}
        title="Add a holiday from this notice"
        onClose={() => setHolidayTarget(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setHolidayTarget(null)}>
              Cancel
            </Button>
            <Button disabled={holiday.isPending || holidayDate === ""} onClick={() => holiday.mutate()}>
              {holiday.isPending ? "Saving…" : "Add holiday"}
            </Button>
          </>
        }
      >
        <Field label="Holiday date">
          <TextInput type="date" value={holidayDate} onChange={(event) => setHolidayDate(event.target.value)} />
        </Field>
        <Field label="Calendar note" hint="Shown in the calendar; defaults to the notice title.">
          <TextInput
            value={holidayDescription}
            onChange={(event) => setHolidayDescription(event.target.value)}
          />
        </Field>
        {holiday.isError ? <ErrorBanner error={holiday.error} /> : null}
      </Modal>
    </section>
  );
}
