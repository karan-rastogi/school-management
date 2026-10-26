import axios from "axios";
import type { ApiError, HealthResponse } from "../types/api";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

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
