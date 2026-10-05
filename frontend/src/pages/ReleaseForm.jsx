import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../services/api.js";

const FIELDS = [
  { key: "completedFeatures", label: "Completed features", hint: "One per line. Type None if there are none." },
  { key: "bugFixes", label: "Bug fixes", hint: "One per line. Type None if there are none." },
  { key: "changedBehaviour", label: "Changed behaviour", hint: "Anything that works differently now. Type None if nothing changed." },
  { key: "qaSummary", label: "QA summary", hint: "What was tested, and where. One result per line" },
  { key: "knownLimitations", label: "Known limitations", hint: "One per line. Type None if there are none." },
  { key: "migrationNotes", label: "Migration and configuration notes", hint: "One per line. Type None if no steps are needed." },
  { key: "affectedUsers", label: "Affected user groups", hint: "For example: customers, admins" },
];

const empty = { version: "", ...Object.fromEntries(FIELDS.map((f) => [f.key, ""])) };
const toPackage = (form) => ({
  version: form.version.trim(),
  ...Object.fromEntries(
    FIELDS.map((f) => [f.key, form[f.key].split("\n").map((l) => l.trim()).filter(Boolean)])
  ),
});

export default function ReleaseForm() {
  const [form, setForm] = useState(empty);
  const [errors, setErrors] = useState([]);
  const [info, setInfo] = useState("");
  const [busy, setBusy] = useState("");
  const navigate = useNavigate();
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (generate) => {
    setBusy(generate ? "Saving and asking the AI…" : "Saving…");
    setErrors([]);
    setInfo("");
    let release;
    try {
      release = await api.createRelease(toPackage(form));
    } catch (e) {
      setErrors(e.errors || [e.message]);
      setBusy("");
      return;
    }
    if (generate) {
      try {
        await api.generate(release.id);
      } catch (e) {
        // The release is saved; the brief page lets the person retry generation.
      }
    }
    navigate(`/releases/${release.id}`);
  };

  const check = async () => {
    setErrors([]);
    setInfo("");
    try {
      await api.validate(toPackage(form));
      setInfo("All required sections are present.");
    } catch (e) {
      setErrors(e.errors || [e.message]);
    }
  };

  return (
    <section className="form-page">
      <h1>New release package</h1>
      <p className="muted">
        Every section is required. If a section does not apply, type None so the brief records that it was considered.
        The server checks this before any AI runs.
      </p>

      {errors.length > 0 && (
        <div className="notice error" role="alert">
          <ul>{errors.map((e) => <li key={e}>{e}</li>)}</ul>
        </div>
      )}
      {info && <div className="notice ok">{info}</div>}

      <label className="field">
        <span>Version <em>required</em></span>
        <input value={form.version} onChange={set("version")} placeholder="1.2.0" />
      </label>

      {FIELDS.map((f) => (
        <label className="field" key={f.key}>
          <span>{f.label} <em>required</em></span>
          <textarea rows={4} value={form[f.key]} onChange={set(f.key)} placeholder={f.hint} />
        </label>
      ))}

      <div className="actions">
        <button className="btn primary" disabled={!!busy} onClick={() => submit(true)}>
          {busy || "Save and generate brief"}
        </button>
        <button className="btn" disabled={!!busy} onClick={() => submit(false)}>Save as draft</button>
        <button className="btn" disabled={!!busy} onClick={check}>Check required sections</button>
      </div>
    </section>
  );
}