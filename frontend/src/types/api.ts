export type ErrorCode =
  | "VALIDATION_ERROR"
  | "MALFORMED_REQUEST"
  | "NOT_FOUND"
  | "METHOD_NOT_ALLOWED"
  | "UNAUTHENTICATED"
  | "FORBIDDEN"
  | "CONFLICT"
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

export interface AdminUser {
  id: number;
  name: string;
  email?: string | null;
  phone?: string | null;
  active: boolean;
  roles: Role[];
}

export interface OtpRequestResponse {
  status: string;
  message: string;
}
