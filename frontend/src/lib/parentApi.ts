import { apiClient } from "./api";
import type { AttendanceQuery, Child, ChildAttendance } from "../types/api";

export async function listChildren(): Promise<Child[]> {
  const response = await apiClient.get<Child[]>("/parent/children");
  return response.data;
}

export async function childAttendance(studentId: number, month: string): Promise<ChildAttendance> {
  const response = await apiClient.get<ChildAttendance>(`/parent/children/${studentId}/attendance`, {
    params: { month },
  });
  return response.data;
}

export async function raiseAttendanceQuery(
  studentId: number,
  input: { date: string; message: string },
): Promise<AttendanceQuery> {
  const response = await apiClient.post<AttendanceQuery>(
    `/parent/children/${studentId}/attendance-queries`,
    input,
  );
  return response.data;
}
