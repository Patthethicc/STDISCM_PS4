import { Navigate } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuth } from "../hooks/useAuth";
import type { Role } from "../types";

export function ProtectedRoute({ role, children }: { role?: Role; children: ReactNode }) {
  const { session, checking } = useAuth();
  if (checking) return <p className="muted center">Loading…</p>;
  if (!session) return <Navigate to="/login" replace />;
  if (role && session.role !== role) return <Navigate to="/" replace />;
  return <>{children}</>;
}
