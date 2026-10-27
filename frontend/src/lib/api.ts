import axios, { type InternalAxiosRequestConfig } from "axios";
import type {
  ApiError,
  CurrentUser,
  HealthResponse,
  OtpRequestResponse,
  Role,
} from "../types/api";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

type RetriableRequestConfig = InternalAxiosRequestConfig & { hasBeenRetried?: boolean };

let refreshInFlight: Promise<unknown> | null = null;

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error)) {
      return Promise.reject(error);
    }

    const request = error.config as RetriableRequestConfig | undefined;
    const isAuthEndpoint = typeof request?.url === "string" && request.url.startsWith("/auth/");

    if (!request || error.response?.status !== 401 || request.hasBeenRetried || isAuthEndpoint) {
      return Promise.reject(error);
    }

    request.hasBeenRetried = true;
    refreshInFlight ??= apiClient.post("/auth/refresh").finally(() => {
      refreshInFlight = null;
    });

    try {
      await refreshInFlight;
    } catch {
      return Promise.reject(error);
    }

    return apiClient.request(request);
  },
);

function isApiError(value: unknown): value is ApiError {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate.code === "string" && typeof candidate.message === "string";
}

export function toApiError(error: unknown): ApiError {
  const timestamp = new Date().toISOString();

  if (axios.isAxiosError(error)) {
    const payload: unknown = error.response?.data;
    if (isApiError(payload)) {
      return payload;
    }
    return {
      timestamp,
      status: error.response?.status ?? 0,
      error: error.code ?? "NETWORK_ERROR",
      code: "NETWORK_ERROR",
      message: error.message,
      path: error.config?.url ?? "",
      fieldErrors: [],
    };
  }

  return {
    timestamp,
    status: 0,
    error: "UNKNOWN_ERROR",
    code: "UNKNOWN_ERROR",
    message: "An unexpected client error occurred",
    path: "",
    fieldErrors: [],
  };
}

export async function fetchHealth(): Promise<HealthResponse> {
  const response = await apiClient.get<HealthResponse>("/api/health");
  return response.data;
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const response = await apiClient.get<CurrentUser>("/me");
  return response.data;
}

export async function requestOtp(phone: string): Promise<OtpRequestResponse> {
  const response = await apiClient.post<OtpRequestResponse>("/auth/otp/request", { phone });
  return response.data;
}

export async function verifyOtp(phone: string, code: string): Promise<CurrentUser> {
  const response = await apiClient.post<CurrentUser>("/auth/otp/verify", { phone, code });
  return response.data;
}

export async function refreshSession(): Promise<CurrentUser> {
  const response = await apiClient.post<CurrentUser>("/auth/refresh");
  return response.data;
}

export async function endSession(): Promise<void> {
  await apiClient.post("/auth/logout");
}

export async function updateActiveRole(role: Role): Promise<CurrentUser> {
  const response = await apiClient.post<CurrentUser>("/me/active-role", { role });
  return response.data;
}

export function googleSignInUrl(): string {
  return `${API_BASE_URL}/oauth2/authorization/google`;
}
