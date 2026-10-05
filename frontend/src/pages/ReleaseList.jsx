import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../services/api.js";

const label = { DRAFT: "Draft", BRIEF_GENERATED: "Brief ready for review", REVIEWED: "Reviewed" };

export default function ReleaseList() {
  const [releases, setReleases] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.listReleases().then(setReleases).catch((e) => setError(e.message));
  }, []);

  if (error) return <div className="notice error">{error}</div>;
  if (!releases) return <p className="muted">Loading releases…</p>;

  return (
    <section>
      <h1>Releases</h1>
      {releases.length === 0 ? (
        <div className="empty">
          <p>No releases yet. Enter a release package to get a technical and a client brief.</p>
          <Link className="btn primary" to="/new">Create a release</Link>
        </div>
      ) : (
        <ul className="release-list">
          {releases.map((r) => {
            const stale = r.statements.filter((s) => s.stale).length;
            return (
              <li key={r.id}>
                <Link to={`/releases/${r.id}`} className="release-row">
                  <span className="version">v{r.version}</span>
                  <span className="muted">{new Date(r.createdAt).toLocaleDateString()}</span>
                  <span className={`status status-${r.status.toLowerCase()}`}>{label[r.status]}</span>
                  {r.impact && <span className={`impact impact-${r.impact.toLowerCase()}`}>{r.impact.toLowerCase()} impact</span>}
                  {stale > 0 && <span className="warn-inline">{stale} stale</span>}
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}