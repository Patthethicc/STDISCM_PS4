import { useState } from "react";

export const GRADE_VALUES = [4.0, 3.5, 3.0, 2.5, 2.0, 1.5, 1.0, 0.5, 0.0];

export interface GradeRow {
  studentId: string;
  name?: string;
  grade: string; // "" = not graded
}

/**
 * Grade entry table. Filled from the roster (course node) when available; when the course
 * node is down, faculty can still add rows by typing student IDs.
 */
export function RosterForm({
  rows,
  setRows,
  allowManual,
}: {
  rows: GradeRow[];
  setRows: (rows: GradeRow[]) => void;
  allowManual: boolean;
}) {
  const [newId, setNewId] = useState("");

  const update = (i: number, grade: string) => setRows(rows.map((r, j) => (i === j ? { ...r, grade } : r)));

  return (
    <div>
      {rows.length > 0 ? (
        <table className="table">
          <thead>
            <tr>
              <th>Student</th>
              <th>ID</th>
              <th className="right">Grade</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={r.studentId}>
                <td>{r.name ?? <span className="muted">—</span>}</td>
                <td className="mono small">{r.studentId}</td>
                <td className="right">
                  <select value={r.grade} onChange={(e) => update(i, e.target.value)}>
                    <option value="">—</option>
                    {GRADE_VALUES.map((v) => (
                      <option key={v} value={v.toFixed(1)}>
                        {v.toFixed(1)}
                      </option>
                    ))}
                  </select>
                  {allowManual && !r.name && (
                    <button
                      className="btn btn-small btn-ghost"
                      type="button"
                      onClick={() => setRows(rows.filter((_, j) => j !== i))}
                    >
                      Remove
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="muted">{allowManual ? "Add students by ID below." : "No students enrolled."}</p>
      )}

      {allowManual && (
        <div className="row">
          <input
            placeholder="Student ID"
            value={newId}
            onChange={(e) => setNewId(e.target.value)}
            className="mono"
          />
          <button
            type="button"
            className="btn btn-small"
            disabled={!newId.trim() || rows.some((r) => r.studentId === newId.trim())}
            onClick={() => {
              setRows([...rows, { studentId: newId.trim(), grade: "" }]);
              setNewId("");
            }}
          >
            Add student
          </button>
        </div>
      )}
    </div>
  );
}
