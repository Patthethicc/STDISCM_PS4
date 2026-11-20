import { api } from "./client";
import type { Session } from "../types";

export const login = (username: string, password: string) =>
  api<Session>("/auth/login", { method: "POST", body: { username, password } });

export const logout = () => api<void>("/auth/logout", { method: "POST" });

export const me = () => api<Session>("/auth/me");
