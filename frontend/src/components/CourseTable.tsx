import type { Course } from "../types";

interface Props {
  courses: Course[];
  /** Student view: course codes already enrolled in, and an enroll handler. */
  enrolledCodes?: Set<string>;
  onEnroll?: (code: string) => void;
  busyCode?: string | null;
}

export function CourseTable({ courses, enrolledCodes, onEnroll, busyCode }: Props) {
  if (courses.length === 0) return <p className="muted">No courses.</p>;
  return (
    <table className="table">
      <thead>
        <tr>
          <th>Code</th>
          <th>Title</th>
          <th>Slots</th>
          <th>Status</th>
          {onEnroll && <th />}
        </tr>
      </thead>
      <tbody>
        {courses.map((c) => {
          const full = c.enrolled >= c.capacity;
          const enrolled = enrolledCodes?.has(c.code);
          return (
            <tr key={c.code}>
              <td className="mono">{c.code}</td>
              <td>{c.title}</td>
              <td>
                {c.enrolled}/{c.capacity}
              </td>
              <td>
                {!c.open ? (
                  <span className="tag tag-closed">Closed</span>
                ) : full ? (
                  <span className="tag tag-full">Full</span>
                ) : (
                  <span className="tag tag-open">Open</span>
                )}
              </td>
              {onEnroll && (
                <td className="right">
                  {enrolled ? (
                    <span className="muted">Enrolled</span>
                  ) : (
                    // Not disabled for full/closed: the service decides, and shows why.
                    <button className="btn btn-small" disabled={busyCode === c.code} onClick={() => onEnroll(c.code)}>
                      {busyCode === c.code ? "Enrolling…" : "Enroll"}
                    </button>
                  )}
                </td>
              )}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
