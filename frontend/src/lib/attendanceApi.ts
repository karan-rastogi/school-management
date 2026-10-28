import { apiClient } from "./api";
import type {
  AttendanceQuery,
  AttendanceQueryStatus,
  AttendanceRoster,
  AttendanceScanRequest,
  AttendanceScanResult,
  AttendanceSource,
  AttendanceStatus,
  MarkableClass,
  SubstitutePermission,
} from "../types/api";

export interface SubmitAttendanceRequest {
  date: string;
  source?: AttendanceSource;
  overrideHoliday?: boolean;
  entries: Array<{ studentId: number; status: AttendanceStatus }>;
}

export async function markableClasses(date: string): Promise<MarkableClass[]> {
  const response = await apiClient.get<MarkableClass[]>("/attendance/classes", { params: { date } });
  return response.data;
}

export async function attendanceRoster(classId: number, date: string): Promise<AttendanceRoster> {
  const response = await apiClient.get<AttendanceRoster>(`/classes/${classId}/attendance`, {
    params: { date },
  });
  return response.data;
}

export async function scanAttendance(body: AttendanceScanRequest): Promise<AttendanceScanResult> {
  const response = await apiClient.post<AttendanceScanResult>("/attendance/scan", body);
  return response.data;
}

export async function submitAttendance(
  classId: number,
  body: SubmitAttendanceRequest,
): Promise<AttendanceRoster> {
  const response = await apiClient.put<AttendanceRoster>(`/classes/${classId}/attendance`, body);
  return response.data;
}

export async function listSubstitutePermissions(classId: number): Promise<SubstitutePermission[]> {
  const response = await apiClient.get<SubstitutePermission[]>(
    `/admin/classes/${classId}/substitute-permissions`,
  );
  return response.data;
}

export async function grantSubstitutePermission(
  classId: number,
  body: { teacherUserId: number | null; validFrom: string; validUntil: string; reason: string },
): Promise<SubstitutePermission> {
  const response = await apiClient.post<SubstitutePermission>(
    `/admin/classes/${classId}/substitute-permissions`,
    body,
  );
  return response.data;
}

export async function revokeSubstitutePermission(id: number): Promise<void> {
  await apiClient.delete(`/admin/substitute-permissions/${id}`);
}

export async function listAttendanceQueries(status?: AttendanceQueryStatus): Promise<AttendanceQuery[]> {
  const response = await apiClient.get<AttendanceQuery[]>("/attendance/queries", { params: { status } });
  return response.data;
}

export async function resolveAttendanceQuery(id: number, response: string): Promise<AttendanceQuery> {
  const result = await apiClient.post<AttendanceQuery>(`/attendance/queries/${id}/resolve`, { response });
  return result.data;
}
