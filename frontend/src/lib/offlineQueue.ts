import type { AttendanceScanRequest } from "../types/api";

const KEY = "attendance.unsynced.scans";

export interface QueuedScan extends AttendanceScanRequest {
  queuedAt: string;
}

function read(): QueuedScan[] {
  try {
    const raw = window.localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as QueuedScan[]) : [];
  } catch {
    return [];
  }
}

function write(items: QueuedScan[]): void {
  window.localStorage.setItem(KEY, JSON.stringify(items));
}

export function listQueued(): QueuedScan[] {
  return read();
}

export function queuedCount(): number {
  return read().length;
}

export function enqueue(scan: AttendanceScanRequest): QueuedScan[] {
  const items = read().filter(
    (item) =>
      !(item.qrToken === scan.qrToken && item.classId === scan.classId && item.date === scan.date),
  );
  items.push({ ...scan, queuedAt: new Date().toISOString() });
  write(items);
  return items;
}

export function removeAt(index: number): QueuedScan[] {
  const items = read();
  items.splice(index, 1);
  write(items);
  return items;
}

export function clearQueue(): void {
  write([]);
}
