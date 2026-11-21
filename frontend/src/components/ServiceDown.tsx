import type { ApiError } from "../api/client";

const LABELS: Record<string, string> = {
  auth: "Login",
  course: "Course",
  grade: "Grade",
  announcement: "Announcement",
  gateway: "Website",
};

/** Shown in place of a section whose node is down. */
export function ServiceDown({ error, onRetry }: { error: ApiError; onRetry?: () => void }) {
  if (!error.serviceDown) {
    return <div className="alert alert-error">{error.message}</div>;
  }
  const label = (error.service && LABELS[error.service]) ?? "This";
  return (
    <div className="alert alert-down" role="status">
      <strong>{label} service is temporarily unavailable.</strong>
      <span> Other features still work.</span>
      {onRetry && (
        <button className="btn btn-small" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}
