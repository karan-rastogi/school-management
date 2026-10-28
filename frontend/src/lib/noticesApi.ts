import { API_BASE_URL, apiClient } from "./api";
import type {
  AssignableClass,
  CalendarDay,
  Notice,
  NoticeInput,
  NoticeUpdateInput,
  PageResponse,
} from "../types/api";

export async function listNotices(page = 0, size = 20): Promise<PageResponse<Notice>> {
  const response = await apiClient.get<PageResponse<Notice>>("/notices", { params: { page, size } });
  return response.data;
}

export async function assignableClasses(): Promise<AssignableClass[]> {
  const response = await apiClient.get<AssignableClass[]>("/notices/assignable-classes");
  return response.data;
}

export async function createNotice(input: NoticeInput): Promise<Notice> {
  const response = await apiClient.post<Notice>("/notices", input);
  return response.data;
}

export async function updateNotice(id: number, input: NoticeUpdateInput): Promise<Notice> {
  const response = await apiClient.put<Notice>(`/notices/${id}`, input);
  return response.data;
}

export async function deleteNotice(id: number): Promise<void> {
  await apiClient.delete(`/notices/${id}`);
}

export async function uploadNoticeAttachment(id: number, file: File): Promise<Notice> {
  const form = new FormData();
  form.append("file", file);
  const response = await apiClient.post<Notice>(`/notices/${id}/attachment`, form);
  return response.data;
}

export async function removeNoticeAttachment(id: number): Promise<void> {
  await apiClient.delete(`/notices/${id}/attachment`);
}

export async function createHolidayFromNotice(
  id: number,
  input: { date: string; description?: string },
): Promise<CalendarDay> {
  const response = await apiClient.post<CalendarDay>(`/notices/${id}/holiday`, input);
  return response.data;
}

export async function listParentNotices(): Promise<Notice[]> {
  const response = await apiClient.get<Notice[]>("/parent/notices");
  return response.data;
}

/** Signed attachment URLs are relative to the API; make them absolute for links and images. */
export function noticeAttachmentUrl(url: string): string {
  return url.startsWith("http") ? url : `${API_BASE_URL}${url}`;
}

/** Plain-text rendering for pasting into WhatsApp (WhatsApp bold is *asterisks*). */
export function formatNoticeForWhatsApp(notice: Notice): string {
  const lines: string[] = [`*${notice.title}*`, "", notice.body, ""];

  const meta: string[] = [];
  if (notice.childName) {
    meta.push(`For ${notice.childName}`);
  } else if (notice.scope === "CLASS" && notice.classLabel) {
    meta.push(`For ${notice.classLabel}`);
  }
  meta.push(`From: ${notice.senderLabel}`);
  if (notice.expiresAt) {
    meta.push(`Valid until ${new Date(notice.expiresAt).toLocaleString()}`);
  }
  lines.push(meta.join(" | "));

  return lines.join("\n");
}
