export type ErrorCode =
  | "VALIDATION_ERROR"
  | "MALFORMED_REQUEST"
  | "NOT_FOUND"
  | "METHOD_NOT_ALLOWED"
  | "UNAUTHENTICATED"
  | "FORBIDDEN"
  | "CONFLICT"
  | "DUPLICATE"
  | "RATE_LIMITED"
  | "OTP_INVALID"
  | "OTP_EXPIRED"
  | "OTP_ATTEMPTS_EXCEEDED"
  | "ACCOUNT_NOT_REGISTERED"
  | "ACCOUNT_DISABLED"
  | "NOT_IMPLEMENTED"
  | "INTERNAL_ERROR"
  | "NETWORK_ERROR"
  | "UNKNOWN_ERROR";

export type Role = "ADMIN" | "TEACHER" | "PARENT";

export type StudentStatus = "ACTIVE" | "LEFT";

export interface FieldViolation {
  field: string;
  message: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: ErrorCode;
  message: string;
  path: string;
  fieldErrors: FieldViolation[];
}

export interface HealthResponse {
  status: string;
  application: string;
  timeZone: string;
  timestamp: string;
}

export interface CurrentUser {
  id: number;
  name: string;
  email?: string | null;
  phone?: string | null;
  roles: Role[];
  activeRole: Role;
}

export interface OtpRequestResponse {
  status: string;
  message: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AcademicYear {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  current: boolean;
}

export interface AcademicYearInput {
  name: string;
  startDate: string;
  endDate: string;
  current: boolean;
}

export interface SchoolClass {
  id: number;
  name: string;
  section: string;
  academicYearId: number;
  academicYearName: string;
}

export interface SchoolClassInput {
  name: string;
  section: string;
  academicYearId: number | null;
}

export interface AdminUser {
  id: number;
  name: string;
  email: string | null;
  phone: string | null;
  active: boolean;
  roles: Role[];
  createdAt: string;
}

export interface UserInput {
  name: string;
  email: string;
  phone: string;
  active: boolean;
  roles: Role[];
}

export interface TeacherAssignment {
  id: number;
  teacherUserId: number;
  teacherName: string;
  classId: number;
  className: string;
  section: string;
  academicYearName: string;
  subjectName: string;
  classTeacher: boolean;
  active: boolean;
}

export interface TeacherAssignmentInput {
  teacherUserId: number | null;
  classId: number | null;
  subjectName: string;
  classTeacher: boolean;
  active: boolean;
}

export interface Student {
  id: number;
  fullName: string;
  admissionNo: string;
  dob: string;
  photoPath: string | null;
  admissionDate: string;
  status: StudentStatus;
  currentClassId: number | null;
  currentClassName: string | null;
  currentSection: string | null;
  rollNo: number | null;
}

export interface StudentInput {
  fullName: string;
  admissionNo: string;
  dob: string;
  photoPath: string;
  admissionDate: string;
  status: StudentStatus;
}

export interface Enrollment {
  id: number;
  classId: number;
  className: string;
  section: string;
  academicYearName: string;
  rollNo: number | null;
  fromDate: string;
  toDate: string | null;
}

export interface EnrollmentInput {
  classId: number | null;
  rollNo: number | null;
  fromDate: string;
  toDate: string;
}

export interface StudentParent {
  parentUserId: number;
  parentName: string | null;
  parentPhone: string | null;
  parentEmail: string | null;
  relationship: string | null;
}

export interface StudentDetail {
  id: number;
  fullName: string;
  admissionNo: string;
  dob: string;
  photoPath: string | null;
  qrToken: string | null;
  admissionDate: string;
  status: StudentStatus;
  enrollments: Enrollment[];
  parents: StudentParent[];
}

export interface Child {
  studentId: number;
  fullName: string | null;
  admissionNo: string | null;
  status: StudentStatus | null;
  relationship: string | null;
  classId: number | null;
  className: string | null;
  section: string | null;
}

export type StudentImportOutcome = "CREATE_STUDENT" | "LINK_PARENT" | "DUPLICATE" | "ERROR";

export interface StudentImportRow {
  rowNumber: number;
  outcome: StudentImportOutcome;
  admissionNo: string;
  studentName: string;
  parentName: string;
  parentPhone: string | null;
  messages: string[];
}

export interface StudentImportReport {
  dryRun: boolean;
  committed: boolean;
  totalRows: number;
  studentsToCreate: number;
  parentsToCreate: number;
  linksToCreate: number;
  duplicateLinks: number;
  errorCount: number;
  canCommit: boolean;
  rows: StudentImportRow[];
}

export type SchoolCalendarDayType = "WORKING" | "HOLIDAY" | "HALF_DAY" | "EXAM";

export interface CalendarDay {
  date: string;
  type?: SchoolCalendarDayType | null;
  working: boolean;
  description?: string | null;
}

export interface WorkingDaysSummary {
  from: string;
  to: string;
  workingDays: number;
}
