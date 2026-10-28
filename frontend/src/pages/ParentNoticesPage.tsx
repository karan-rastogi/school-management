import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Badge, Button, Card, ErrorBanner, Loading, PageHeader } from "../components/ui";
import * as noticesApi from "../lib/noticesApi";
import type { Notice } from "../types/api";

export function ParentNoticesPage() {
  const [toast, setToast] = useState<string | null>(null);

  const notices = useQuery({ queryKey: ["parent", "notices"], queryFn: noticesApi.listParentNotices });

  async function copyForWhatsApp(notice: Notice) {
    try {
      await navigator.clipboard.writeText(noticesApi.formatNoticeForWhatsApp(notice));
      setToast("Copied — ready to paste into WhatsApp.");
    } catch {
      setToast("Could not copy to the clipboard.");
    }
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title="Notices"
        description="School-wide news and notices from each of your children's classes."
      />

      {toast ? (
        <p className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
          {toast}
        </p>
      ) : null}

      {notices.isError ? <ErrorBanner error={notices.error} /> : null}
      {notices.isPending ? <Loading label="Loading notices…" /> : null}

      {notices.data && notices.data.length === 0 ? (
        <Card className="p-4">
          <p className="text-sm text-slate-600">There are no notices right now.</p>
        </Card>
      ) : null}

      {notices.data?.map((notice) => (
        <Card key={notice.id} className="space-y-3 p-4">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div className="space-y-1">
              <h2 className="text-base font-semibold">{notice.title}</h2>
              <p className="text-xs text-slate-500">
                {notice.senderLabel} · {new Date(notice.createdAt).toLocaleString()}
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {notice.scope === "GLOBAL" ? (
                <Badge tone="indigo">School-wide</Badge>
              ) : (
                <Badge tone="slate">{notice.childName ?? notice.classLabel ?? "Class"}</Badge>
              )}
              {notice.edited ? <Badge tone="amber">edited</Badge> : null}
            </div>
          </div>

          <p className="whitespace-pre-wrap text-sm text-slate-700">{notice.body}</p>

          {notice.expiresAt ? (
            <p className="text-xs text-slate-500">
              Valid until {new Date(notice.expiresAt).toLocaleString()}
            </p>
          ) : null}

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

          <div className="border-t border-slate-100 pt-3">
            <Button variant="secondary" onClick={() => void copyForWhatsApp(notice)}>
              Copy for WhatsApp
            </Button>
          </div>
        </Card>
      ))}
    </section>
  );
}
