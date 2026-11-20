import { useState } from "react";
import { ApiError } from "../api/client";
import { enroll, listCourses, myEnrollments } from "../api/courses";
import { CourseTable } from "../components/CourseTable";
import { ServiceDown } from "../components/ServiceDown";
import { useAuth } from "../hooks/useAuth";
import { useService } from "../hooks/useService";

export function CoursesPage() {
  const { session } = useAuth();
  const isStudent = session?.role === "STUDENT";
  const courses = useService(listCourses);
  const mine = useService(() => (isStudent ? myEnrollments() : Promise.resolve([])), [isStudent]);
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<{ kind: "ok" | "error"; text: string } | null>(null);

  async function onEnroll(code: string) {
    setBusy(code);
    setMessage(null);
    try {
      await enroll(code);
      setMessage({ kind: "ok", text: `Enrolled in ${code}.` });
    } catch (e) {
      setMessage({ kind: "error", text: e instanceof ApiError ? e.message : "Enrollment failed." });
    } finally {
      setBusy(null);
      void courses.reload();
      void mine.reload();
    }
  }

  return (
    <div className="page">
      <h2>Available Courses</h2>
      {message && <div className={`alert ${message.kind === "ok" ? "alert-ok" : "alert-error"}`}>{message.text}</div>}
      {courses.loading && !courses.data ? (
        <p className="muted">Loading…</p>
      ) : courses.error ? (
        <ServiceDown error={courses.error} onRetry={courses.reload} />
      ) : (
        <CourseTable
          courses={courses.data ?? []}
          enrolledCodes={isStudent ? new Set((mine.data ?? []).map((c) => c.code)) : undefined}
          onEnroll={isStudent ? onEnroll : undefined}
          busyCode={busy}
        />
      )}
    </div>
  );
}
