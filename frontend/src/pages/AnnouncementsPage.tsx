import { useState, type FormEvent } from "react";
import { ApiError } from "../api/client";
import { listAnnouncements, postAnnouncement } from "../api/announcements";
import { ServiceDown } from "../components/ServiceDown";
import { useAuth } from "../hooks/useAuth";
import { useService } from "../hooks/useService";

export function AnnouncementsPage() {
  const { session } = useAuth();
  const { data, error, loading, reload } = useService(listAnnouncements);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [postError, setPostError] = useState<string | null>(null);

  async function post(e: FormEvent) {
    e.preventDefault();
    setPostError(null);
    try {
      await postAnnouncement(title, body);
      setTitle("");
      setBody("");
      void reload();
    } catch (err) {
      setPostError(err instanceof ApiError ? err.message : "Couldn't post.");
    }
  }

  return (
    <div className="page">
      <h2>Announcements</h2>
      {error ? (
        <ServiceDown error={error} onRetry={reload} />
      ) : (
        <>
          {session?.role === "FACULTY" && (
            <form className="card stack" onSubmit={post}>
              <input placeholder="Title" value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />
              <textarea placeholder="Message" value={body} maxLength={5000} rows={3} onChange={(e) => setBody(e.target.value)} />
              {postError && <div className="alert alert-error">{postError}</div>}
              <button className="btn" disabled={!title.trim() || !body.trim()}>
                Post
              </button>
            </form>
          )}
          {loading && !data ? (
            <p className="muted">Loading…</p>
          ) : data && data.length > 0 ? (
            data.map((a) => (
              <article key={a.id} className="card announcement">
                <h3>{a.title}</h3>
                <p>{a.body}</p>
                <p className="muted small">
                  {a.authorName} · {new Date(a.createdAt * 1000).toLocaleString()}
                </p>
              </article>
            ))
          ) : (
            <p className="muted">No announcements.</p>
          )}
        </>
      )}
    </div>
  );
}
