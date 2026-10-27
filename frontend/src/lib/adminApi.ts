import { apiClient } from "./api";
import type {
  AcademicYear,
  AcademicYearInput,
  AdminUser,
  CalendarDay,
  Child,
  Enrollment,
  EnrollmentInput,
  PageResponse,
  Role,
  SchoolCalendarDayType,
  SchoolClass,
  SchoolClassInput,
  Student,
  StudentDetail,
  StudentImportReport,
  StudentInput,
  StudentParent,
  StudentStatus,
  TeacherAssignment,
  TeacherAssignmentInput,
  UserInput,
  WorkingDaysSummary,
} from "../types/api";

export interface PageParams {
  page?: number;
  size?: number;
}

/* Academic years */

export interface AcademicYearListParams extends PageParams {
  query?: string;
}

export async function listAcademicYears(
  params: AcademicYearListParams,
): Promise<PageResponse<AcademicYear>> {
  const response = await apiClient.get<PageResponse<AcademicYear>>("/admin/academic-years", { params });
  return response.data;
}

export async function academicYearOptions(): Promise<AcademicYear[]> {
  const response = await apiClient.get<AcademicYear[]>("/admin/academic-years/options");
  return response.data;
}

export async function createAcademicYear(input: AcademicYearInput): Promise<AcademicYear> {
  const response = await apiClient.post<AcademicYear>("/admin/academic-years", input);
  return response.data;
}

export async function updateAcademicYear(id: number, input: AcademicYearInput): Promise<AcademicYear> {
  const response = await apiClient.put<AcademicYear>(`/admin/academic-years/${id}`, input);
  return response.data;
}

export async function deleteAcademicYear(id: number): Promise<void> {
  await apiClient.delete(`/admin/academic-years/${id}`);
}

/* Classes */

export interface ClassListParams extends PageParams {
  query?: string;
  academicYearId?: number;
}

export async function listClasses(params: ClassListParams): Promise<PageResponse<SchoolClass>> {
  const response = await apiClient.get<PageResponse<SchoolClass>>("/admin/classes", { params });
  return response.data;
}

export async function classOptions(academicYearId?: number): Promise<SchoolClass[]> {
  const response = await apiClient.get<SchoolClass[]>("/admin/classes/options", {
    params: { academicYearId },
  });
  return response.data;
}

export async function createClass(input: SchoolClassInput): Promise<SchoolClass> {
  const response = await apiClient.post<SchoolClass>("/admin/classes", input);
  return response.data;
}

export async function updateClass(id: number, input: SchoolClassInput): Promise<SchoolClass> {
  const response = await apiClient.put<SchoolClass>(`/admin/classes/${id}`, input);
  return response.data;
}

export async function deleteClass(id: number): Promise<void> {
  await apiClient.delete(`/admin/classes/${id}`);
}

/* Users (teachers, parents, admins) */

export interface UserListParams extends PageParams {
  query?: string;
  role?: Role;
  active?: boolean;
}

export async function listUsers(params: UserListParams): Promise<PageResponse<AdminUser>> {
  const response = await apiClient.get<PageResponse<AdminUser>>("/admin/users", { params });
  return response.data;
}

export async function userOptions(role?: Role): Promise<AdminUser[]> {
  const response = await apiClient.get<AdminUser[]>("/admin/users/options", { params: { role } });
  return response.data;
}

export async function createUser(input: UserInput): Promise<AdminUser> {
  const response = await apiClient.post<AdminUser>("/admin/users", input);
  return response.data;
}

export async function updateUser(id: number, input: Omit<UserInput, "roles">): Promise<AdminUser> {
  const response = await apiClient.put<AdminUser>(`/admin/users/${id}`, input);
  return response.data;
}

export async function replaceUserRoles(id: number, roles: Role[]): Promise<AdminUser> {
  const response = await apiClient.put<AdminUser>(`/admin/users/${id}/roles`, { roles });
  return response.data;
}

export async function deleteUser(id: number): Promise<void> {
  await apiClient.delete(`/admin/users/${id}`);
}

/* Teacher assignments */

export interface TeacherAssignmentListParams extends PageParams {
  classId?: number;
  teacherUserId?: number;
}

export async function listTeacherAssignments(
  params: TeacherAssignmentListParams,
): Promise<PageResponse<TeacherAssignment>> {
  const response = await apiClient.get<PageResponse<TeacherAssignment>>("/admin/teacher-assignments", {
    params,
  });
  return response.data;
}

export async function createTeacherAssignment(
  input: TeacherAssignmentInput,
): Promise<TeacherAssignment> {
  const response = await apiClient.post<TeacherAssignment>("/admin/teacher-assignments", input);
  return response.data;
}

export async function updateTeacherAssignment(
  id: number,
  input: TeacherAssignmentInput,
): Promise<TeacherAssignment> {
  const response = await apiClient.put<TeacherAssignment>(`/admin/teacher-assignments/${id}`, input);
  return response.data;
}

export async function deleteTeacherAssignment(id: number): Promise<void> {
  await apiClient.delete(`/admin/teacher-assignments/${id}`);
}

/* Students */

export interface StudentListParams extends PageParams {
  query?: string;
  status?: StudentStatus;
  classId?: number;
}

export async function listStudents(params: StudentListParams): Promise<PageResponse<Student>> {
  const response = await apiClient.get<PageResponse<Student>>("/admin/students", { params });
  return response.data;
}

export async function getStudent(id: number): Promise<StudentDetail> {
  const response = await apiClient.get<StudentDetail>(`/admin/students/${id}`);
  return response.data;
}

function studentPayload(input: StudentInput) {
  return {
    fullName: input.fullName,
    admissionNo: input.admissionNo,
    dob: input.dob || null,
    photoPath: input.photoPath || null,
    admissionDate: input.admissionDate || null,
    status: input.status,
  };
}

export async function createStudent(input: StudentInput): Promise<Student> {
  const response = await apiClient.post<Student>("/admin/students", studentPayload(input));
  return response.data;
}

export async function updateStudent(id: number, input: StudentInput): Promise<Student> {
  const response = await apiClient.put<Student>(`/admin/students/${id}`, studentPayload(input));
  return response.data;
}

export async function regenerateQrToken(id: number): Promise<string> {
  const response = await apiClient.post<{ qrToken: string }>(`/admin/students/${id}/qr-token`);
  return response.data.qrToken;
}

export async function enrollStudent(studentId: number, input: EnrollmentInput): Promise<Enrollment> {
  const response = await apiClient.post<Enrollment>(`/admin/students/${studentId}/enrollments`, {
    classId: input.classId,
    rollNo: input.rollNo,
    fromDate: input.fromDate || null,
    toDate: input.toDate || null,
  });
  return response.data;
}

export async function removeEnrollment(studentId: number, enrollmentId: number): Promise<void> {
  await apiClient.delete(`/admin/students/${studentId}/enrollments/${enrollmentId}`);
}

/* Parent - student links */

export async function studentParents(studentId: number): Promise<StudentParent[]> {
  const response = await apiClient.get<StudentParent[]>(`/admin/students/${studentId}/parents`);
  return response.data;
}

export async function parentChildren(parentUserId: number): Promise<Child[]> {
  const response = await apiClient.get<Child[]>(`/admin/parents/${parentUserId}/students`);
  return response.data;
}

export async function linkParentStudent(input: {
  parentUserId: number | null;
  studentId: number | null;
  relationship: string;
}): Promise<StudentParent> {
  const response = await apiClient.post<StudentParent>("/admin/parent-student-links", input);
  return response.data;
}

export async function unlinkParentStudent(parentUserId: number, studentId: number): Promise<void> {
  await apiClient.delete(`/admin/parent-student-links/${parentUserId}/${studentId}`);
}

/* CSV import */

export async function importStudents(csv: string, dryRun: boolean): Promise<StudentImportReport> {
  const response = await apiClient.post<StudentImportReport>("/admin/students/import", { csv, dryRun });
  return response.data;
}

/* School calendar */

export async function calendarRange(from: string, to: string): Promise<CalendarDay[]> {
  const response = await apiClient.get<CalendarDay[]>("/admin/calendar", { params: { from, to } });
  return response.data;
}

export async function workingDays(from: string, to: string): Promise<WorkingDaysSummary> {
  const response = await apiClient.get<WorkingDaysSummary>("/admin/calendar/working-days", {
    params: { from, to },
  });
  return response.data;
}

export async function markCalendarDay(
  date: string,
  input: { type: SchoolCalendarDayType; description: string },
): Promise<CalendarDay> {
  const response = await apiClient.put<CalendarDay>(`/admin/calendar/${date}`, input);
  return response.data;
}

export async function clearCalendarDay(date: string): Promise<void> {
  await apiClient.delete(`/admin/calendar/${date}`);
}
