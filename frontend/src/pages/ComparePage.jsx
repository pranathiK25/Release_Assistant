import { useEffect, useState } from "react";
import { api } from "../services/api.js";

export default function ComparePage() {
  const [releases, setReleases] = useState([]);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.listReleases().then(setReleases).catch((e) => setError(e.message));
  }, []);

  const run = async () => {
    setError("");
    setResult(null);
    try {
      setResult(await api.compare(from, to));
    } catch (e) {
      setError(e.message);
    }
  };

  const sections = result ? Object.entries(result.sections) : [];

  return (
    <section>
      <h1>Compare versions</h1>
      <div className="compare-controls">
        <label className="field"><span>Older version</span>
          <select value={from} onChange={(e) => setFrom(e.target.value)}>
            <option value="">Select…</option>
            {releases.map((r) => <option key={r.id} value={r.id}>v{r.version}</option>)}
          </select>
        </label>
        <label className="field"><span>Newer version</span>
          <select value={to} onChange={(e) => setTo(e.target.value)}>
            <option value="">Select…</option>
            {releases.map((r) => <option key={r.id} value={r.id}>v{r.version}</option>)}
          </select>
        </label>
        <button className="btn primary" disabled={!from || !to || from === to} onClick={run}>Compare</button>
      </div>

      {error && <div className="notice error">{error}</div>}

      {result && (
        <div className="diff">
          <h2>v{result.from} to v{result.to}</h2>
          {sections.length === 0 && <p className="muted">The two packages are identical.</p>}
          {sections.map(([label, d]) => (
            <div key={label} className="diff-section">
              <h3>{label}</h3>
              {d.added.map((t) => <div key={"a" + t} className="diff-line added"><span>+</span>{t}</div>)}
              {d.removed.map((t) => <div key={"r" + t} className="diff-line removed"><span>−</span>{t}</div>)}
            </div>
          ))}

          <h2>Stale statements in v{result.from}</h2>
          {result.staleStatements.length === 0 ? (
            <p className="muted">
              No statements in v{result.from} were made untrue by v{result.to}. Stale checks run when the newer brief is generated.
            </p>
          ) : (
            result.staleStatements.map((s) => (
              <div key={s.id} className="notice stale">
                <strong>{s.text}</strong>
                <div>{s.staleReason}</div>
              </div>
            ))
          )}
        </div>
      )}
    </section>
  );
}