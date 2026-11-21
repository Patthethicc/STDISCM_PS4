import { myEnrollments } from "../api/courses";
import { CourseTable } from "../components/CourseTable";
import { ServiceDown } from "../components/ServiceDown";
import { useService } from "../hooks/useService";

export function MyEnrollmentsPage() {
  const { data, error, loading, reload } = useService(myEnrollments);
  return (
    <div className="page">
      <h2>My Enrollments</h2>
      {loading && !data ? (
        <p className="muted">Loading…</p>
      ) : error ? (
        <ServiceDown error={error} onRetry={reload} />
      ) : data && data.length > 0 ? (
        <CourseTable courses={data} />
      ) : (
        <p className="muted">You're not enrolled in any course yet.</p>
      )}
    </div>
  );
}
