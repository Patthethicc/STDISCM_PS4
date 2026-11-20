import { useEffect, useState } from "react";
import { ApiError } from "../api/client";
import { roster } from "../api/courses";
import { teachingCourses, uploadGrades } from "../api/grades";
import { RosterForm, type GradeRow } from "../components/RosterForm";
import { ServiceDown } from "../components/ServiceDown";
import { useService } from "../hooks/useService";

/**
 * Uses two nodes: the grade node (my courses + upload) and the course node (roster).
 * If the course node is down, faculty can still upload by typing student IDs.
 */
export function UploadGradesPage() {
  const teaching = useService(teachingCourses);
  const [courseCode, setCourseCode] = useState("");
  const [term, setTerm] = useState("T1 2026-2027");
  const [rows, setRows] = useState<GradeRow[]>([]);
  const [rosterError, setRosterError] = useState<ApiError | null>(null);
  const [loadingRoster, setLoadingRoster] = useState(false);
  const [message, setMessage] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!courseCode && teaching.data?.length) setCourseCode(teaching.data[0].courseCode);
  }, [teaching.data, courseCode]);

  async function loadRoster() {
    if (!courseCode) return;
    setLoadingRoster(true);
    setRosterError(null);
    setMessage(null);
    try {
      const r = await roster(courseCode);
      setRows(r.students.map((s) => ({ studentId: s.studentId, name: s.studentName, grade: "" })));
    } catch (e) {
      setRosterError(e instanceof ApiError ? e : new ApiError(0, "ERROR", String(e)));
      setRows([]);
    } finally {
      setLoadingRoster(false);
    }
  }

  useEffect(() => {
    void loadRoster();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseCode]);

  async function submit() {
    const graded = rows.filter((r) => r.grade !== "");
    if (!graded.length) return;
    setSaving(true);
    setMessage(null);
    try {
      const res = await uploadGrades({
        courseCode,
        term,
        grades: graded.map((r) => ({ studentId: r.studentId, grade: Number(r.grade) })),
      });
      setMessage({ kind: "ok", text: `Saved ${res.saved} grade${res.saved === 1 ? "" : "s"}.` });
    } catch (e) {
      const err = e instanceof ApiError ? e : null;
      setMessage({
        kind: "error",
        text: err?.serviceDown ? "The grade service is temporarily unavailable." : (err?.message ?? "Upload failed."),
      });
    } finally {
      setSaving(false);
    }
  }

  if (teaching.error) {
    return (
      <div className="page">
        <h2>Upload Grades</h2>
        <ServiceDown error={teaching.error} onRetry={teaching.reload} />
      </div>
    );
  }

  const manual = rosterError?.serviceDown ?? false;

  return (
    <div className="page">
      <h2>Upload Grades</h2>
      <div className="row">
        <label>
          Course
          <select value={courseCode} onChange={(e) => setCourseCode(e.target.value)} disabled={teaching.loading}>
            {(teaching.data ?? []).map((c) => (
              <option key={c.courseCode} value={c.courseCode}>
                {c.courseCode} — {c.courseTitle}
              </option>
            ))}
          </select>
        </label>
        <label>
          Term
          <input value={term} onChange={(e) => setTerm(e.target.value)} />
        </label>
      </div>

      {teaching.data && teaching.data.length === 0 && <p className="muted">You aren't assigned to any course.</p>}

      {rosterError && <ServiceDown error={rosterError} onRetry={loadRoster} />}
      {manual && <p className="muted">You can still upload grades by entering student IDs.</p>}

      {loadingRoster ? (
        <p className="muted">Loading roster…</p>
      ) : (
        courseCode && (!rosterError || manual) && <RosterForm rows={rows} setRows={setRows} allowManual={manual} />
      )}

      {message && <div className={`alert ${message.kind === "ok" ? "alert-ok" : "alert-error"}`}>{message.text}</div>}
      <button className="btn" disabled={saving || !courseCode || !rows.some((r) => r.grade !== "")} onClick={submit}>
        {saving ? "Saving…" : "Upload grades"}
      </button>
    </div>
  );
}
