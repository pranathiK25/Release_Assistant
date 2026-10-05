import { useState } from "react";

export default function StatementCard({ statement, evidenceIndex, locked, onReview }) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(statement.text);
  const s = statement;

  const save = async () => {
    await onReview(s.id, { text });
    setEditing(false);
  };

  return (
    <article className={`statement st-${s.status.toLowerCase()} ${s.stale ? "is-stale" : ""}`}>
      <div className="statement-head">
        <span className="tag">{s.section.toLowerCase()}</span>
        <span className={`status status-${s.status.toLowerCase()}`}>{s.status.toLowerCase()}</span>
      </div>

      {editing ? (
        <textarea value={text} onChange={(e) => setText(e.target.value)} rows={3} />
      ) : (
        <p className={s.status === "REJECTED" ? "struck" : ""}>{s.text}</p>
      )}

      <div className="evidence">
        {s.evidence.map((id) => (
          <span key={id} className="chip" tabIndex={0} title={evidenceIndex[id]}>
            {id}
            <span className="chip-text">{evidenceIndex[id]}</span>
          </span>
        ))}
        {s.unsupported && (
          <span className="warn-inline">No evidence linked. Edit the statement or reject it.</span>
        )}
      </div>

      {s.stale && <div className="notice stale">Stale. {s.staleReason}</div>}
      {s.text !== s.originalText && !editing && <div className="muted small">Edited from the AI draft.</div>}

      {!locked && (
        <div className="actions">
          {editing ? (
            <>
              <button className="btn primary" onClick={save}>Save edit</button>
              <button className="btn" onClick={() => { setText(s.text); setEditing(false); }}>Cancel</button>
            </>
          ) : (
            <>
              <button className="btn" onClick={() => onReview(s.id, { status: "ACCEPTED" })} disabled={s.status === "ACCEPTED"}>Accept</button>
              <button className="btn" onClick={() => setEditing(true)}>Edit</button>
              <button className="btn danger" onClick={() => onReview(s.id, { status: "REJECTED" })} disabled={s.status === "REJECTED"}>Reject</button>
            </>
          )}
        </div>
      )}
    </article>
  );
}