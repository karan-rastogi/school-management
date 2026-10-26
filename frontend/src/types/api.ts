export type ErrorCode =
  | "VALIDATION_ERROR"
  | "MALFORMED_REQUEST"
  | "NOT_FOUND"
  | "METHOD_NOT_ALLOWED"
  | "UNAUTHENTICATED"
  | "FORBIDDEN"
  | "CONFLICT"
  | "INTERNAL_ERROR"
  | "NETWORK_ERROR"
  | "UNKNOWN_ERROR";

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
