import { apiRequest } from "./http";
import type { AuthResponse, LoginRequest, SignUpRequest } from "./types";

export function signUp(request: SignUpRequest) {
  return apiRequest<AuthResponse>("/api/auth/signup", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function login(request: LoginRequest) {
  return apiRequest<AuthResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(request),
  });
}
