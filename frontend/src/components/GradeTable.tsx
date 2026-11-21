import type { Grade } from "../types";

export function GradeTable({ grades }: { grades: Grade[] }) {
  if (grades.length === 0) return <p className="muted">No grades yet.</p>;

  const byTerm = new Map<string, Grade[]>();
  for (const g of grades) byTerm.set(g.term, [...(byTerm.get(g.term) ?? []), g]);

  return (
    <>
      {[...byTerm.entries()].map(([term, list]) => (
        <section key={term} className="term">
          <h3>{term}</h3>
          <table className="table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Title</th>
                <th className="right">Grade</th>
              </tr>
            </thead>
            <tbody>
              {list.map((g) => (
                <tr key={g.courseCode}>
                  <td className="mono">{g.courseCode}</td>
                  <td>{g.courseTitle}</td>
                  <td className="right mono">{g.grade.toFixed(1)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ))}
    </>
  );
}
