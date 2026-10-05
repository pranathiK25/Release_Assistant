import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../services/api.js";
import StatementCard from "../components/StatementCard.jsx";

export default function BriefPage() {
  const { id } = useParams();
  const [release, setRelease] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState("");
  const [reviewer, setReviewer] = useState("");

  const load = useCallback(
    () => api.getRelease(id).then(setRelease).catch((e) => setError(e.message)),
    [id]
  );
  useEffect(() => { load(); }, [load]);

  const run = async (fn, message = "Working…") => {
    setBusy(message);
    setError("");
    try {
      await fn();
    } catch (e) {
      setError(e.message);
    }
    await load();
    setBusy("");
  };

  if (error && !release) return <div className="notice error">{error}</div>;
  if (!release) return <p className="muted">Loading release…</p>;

  const locked = release.status === "REVIEWED";
  const findings = release.findings ?? [];
  const impacts = release.changeImpacts ?? [];
  const missing = release.missingInformation ?? [];
  const risks = findings.filter((f) => f.type === "RISK");
  const unsupported = findings.filter((f) => f.type === "UNSUPPORTED_CLAIM");
  const pending = release.statements.filter((s) => s.status === "PENDING").length;

  const review = (sid, patch) => run(() => api.reviewStatement(sid, patch));
  const generate = () => run(() => api.generate(id), "The AI is analysing this release…");
  const byAudience = (a) => release.statements.filter((s) => s.audience === a);

  const Chips = ({ ids }) => (
    <div className="evidence">
      {ids.map((e) => (
        <span key={e} className="chip" tabIndex={0} title={release.evidenceIndex[e]}>
          {e}<span className="chip-text">{release.evidenceIndex[e]}</span>
        </span>
      ))}
    </div>
  );

  const Group = ({ title, items }) => (
    <div className="brief-col">
      <h2>{title}</h2>
      {items.length === 0 && <p className="muted">Nothing generated.</p>}
      {items.map((s) => (
        <StatementCard key={s.id} statement={s} evidenceIndex={release.evidenceIndex}
                       locked={locked || !!busy} onReview={review} />
      ))}
    </div>
  );

  return (
    <section>
      <div className="page-head">
        <h1>v{release.version}</h1>
        <span className={`status status-${release.status.toLowerCase()}`}>
          {release.status.replace("_", " ").toLowerCase()}
        </span>
        {release.impact && (
          <span className={`impact impact-${release.impact.toLowerCase()}`}>
            {release.impact.toLowerCase()} impact (AI suggestion)
          </span>
        )}
        {locked && <Link className="btn primary" to={`/releases/${id}/final`}>Open final brief</Link>}
      </div>

      {locked && (
        <p className="muted">Reviewed by {release.reviewedBy} on {new Date(release.reviewedAt).toLocaleString()}.</p>
      )}
      {error && <div className="notice error">{error}</div>}
      {busy && <div className="notice ok">{busy}</div>}

      {release.statements.length === 0 ? (
        <div className="empty">
          <p>This release has no brief yet.</p>
          <button className="btn primary" disabled={!!busy} onClick={generate}>Generate brief</button>
        </div>
      ) : (
        <>
          {release.impactRationale && <p className="muted">{release.impactRationale}</p>}

          {missing.length > 0 && (
            <div className="panel">
              <h2>Missing information</h2>
              <ul>{missing.map((m, i) => <li key={i}>{m}</li>)}</ul>
            </div>
          )}

          {unsupported.length > 0 && (
            <div className="panel">
              <h2>Claims not supported by the QA evidence</h2>
              {unsupported.map((f, i) => (
                <div key={i} className="notice warn">{f.message}<Chips ids={f.evidence} /></div>
              ))}
            </div>
          )}

          {risks.length > 0 && (
            <div className="panel">
              <h2>Known risks</h2>
              {risks.map((f, i) => (
                <div key={i} className="notice warn">{f.message}<Chips ids={f.evidence} /></div>
              ))}
            </div>
          )}

          {impacts.length > 0 && (
            <div className="panel">
              <h2>Change impact</h2>
              <table className="impact-table">
                <tbody>
                  {impacts.map((c) => (
                    <tr key={c.evidenceId}>
                      <td><span className="chip">{c.evidenceId}</span></td>
                      <td><span className={`impact impact-${c.level.toLowerCase()}`}>{c.level.toLowerCase()}</span></td>
                      <td>{release.evidenceIndex[c.evidenceId]}<div className="muted small">{c.rationale}</div></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="brief-grid">
            <Group title="Internal technical summary" items={byAudience("TECHNICAL")} />
            <Group title="Client summary" items={byAudience("CLIENT")} />
          </div>

          {!locked && (
            <div className="actions sticky">
              <input className="reviewer" value={reviewer} onChange={(e) => setReviewer(e.target.value)}
                     placeholder="Your name" aria-label="Reviewer name" />
              <button className="btn primary" disabled={!!busy || pending > 0 || !reviewer.trim()}
                      onClick={() => run(() => api.finalize(id, reviewer.trim()))}>
                Approve and finalize
              </button>
              <button className="btn" disabled={!!busy} onClick={generate}>Regenerate (resets reviews)</button>
              <span className="muted">
                {pending > 0 ? `${pending} statement(s) still to review` : "All statements reviewed"}
              </span>
            </div>
          )}
        </>
      )}

      <details className="package">
        <summary>Release package and evidence ids</summary>
        <dl>
          {Object.entries(release.evidenceIndex).map(([k, v]) => (
            <div key={k}><dt>{k}</dt><dd>{v}</dd></div>
          ))}
        </dl>
      </details>
    </section>
  );
}