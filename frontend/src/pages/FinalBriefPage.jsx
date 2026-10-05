import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../services/api.js";

export default function FinalBriefPage() {
  const { id } = useParams();
  const [brief, setBrief] = useState(null);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    api.finalBrief(id).then(setBrief).catch((e) => setError(e.message));
  }, [id]);

  if (error) {
    return (
      <div>
        <div className="notice error">{error}</div>
        <Link className="btn" to={`/releases/${id}`}>Back to review</Link>
      </div>
    );
  }
  if (!brief) return <p className="muted">Loading final brief…</p>;

  const copy = async () => {
    await navigator.clipboard.writeText(brief.markdown);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const download = () => {
    const blob = new Blob([brief.markdown], { type: "text/markdown" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `release-${brief.version}-brief.md`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  const cite = (ids) => (ids.length ? ` [${ids.join(", ")}]` : "");

  const Audience = ({ title, groups }) => (
    <div className="final-block">
      <h2>{title}</h2>
      {Object.keys(groups).length === 0 && <p className="muted">No approved statements.</p>}
      {Object.entries(groups).map(([heading, lines]) => (
        <div key={heading}>
          <h3>{heading}</h3>
          <ul>
            {lines.map((l, i) => (
              <li key={i}>{l.text}<span className="cite">{cite(l.evidence)}</span></li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );

  return (
    <section className="final">
      <div className="page-head no-print">
        <h1>Final brief v{brief.version}</h1>
        <Link to={`/releases/${id}`}>Back to review</Link>
      </div>
      <div className="actions no-print">
        <button className="btn primary" onClick={copy}>{copied ? "Copied" : "Copy as Markdown"}</button>
        <button className="btn" onClick={download}>Download .md</button>
        <button className="btn" onClick={() => window.print()}>Print or save as PDF</button>
      </div>

      <p className="muted">
        Drafted by AI. Reviewed and approved by {brief.reviewedBy} on {new Date(brief.reviewedAt).toLocaleString()}.
        Suggested impact (AI, not an approval): {brief.impact?.toLowerCase()}.
      </p>

      <Audience title="Client summary" groups={brief.client} />
      <Audience title="Internal technical summary" groups={brief.technical} />

      <div className="final-block">
        <h2>Known risks</h2>
        {brief.risks.length === 0 ? <p className="muted">None identified.</p> : (
          <ul>{brief.risks.map((f, i) => <li key={i}>{f.message}<span className="cite">{cite(f.evidence)}</span></li>)}</ul>
        )}
        <h2>Claims not supported by QA evidence</h2>
        {brief.unsupportedClaims.length === 0 ? <p className="muted">None identified.</p> : (
          <ul>{brief.unsupportedClaims.map((f, i) => <li key={i}>{f.message}<span className="cite">{cite(f.evidence)}</span></li>)}</ul>
        )}
        <h2>Missing information</h2>
        {brief.missingInformation.length === 0 ? <p className="muted">None identified.</p> : (
          <ul>{brief.missingInformation.map((m, i) => <li key={i}>{m}</li>)}</ul>
        )}
      </div>

      <div className="final-block">
        <h2>Evidence</h2>
        <dl className="evidence-list">
          {Object.entries(brief.evidence).map(([k, v]) => (
            <div key={k}><dt>{k}</dt><dd>{v}</dd></div>
          ))}
        </dl>
      </div>
    </section>
  );
}