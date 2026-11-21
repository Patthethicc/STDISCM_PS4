import { myGrades } from "../api/grades";
import { GradeTable } from "../components/GradeTable";
import { ServiceDown } from "../components/ServiceDown";
import { useService } from "../hooks/useService";

export function MyGradesPage() {
  const { data, error, loading, reload } = useService(myGrades);
  return (
    <div className="page">
      <h2>My Grades</h2>
      {loading && !data ? (
        <p className="muted">Loading…</p>
      ) : error ? (
        <ServiceDown error={error} onRetry={reload} />
      ) : (
        <GradeTable grades={data ?? []} />
      )}
    </div>
  );
}
