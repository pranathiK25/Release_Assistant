const BASE = import.meta.env.VITE_API_URL || "";

async function request(path, options = {}) {
  const res = await fetch(`${BASE}/api${path}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    const errors = body.errors || ["Something went wrong."];
    const err = new Error(errors.join(" "));
    err.errors = errors;
    throw err;
  }
  return body;
}

export const api = {
  validate: (pkg) => request("/releases/validate", { method: "POST", body: JSON.stringify(pkg) }),
  createRelease: (pkg) => request("/releases", { method: "POST", body: JSON.stringify(pkg) }),
  listReleases: () => request("/releases"),
  getRelease: (id) => request(`/releases/${id}`),
  generate: (id) => request(`/releases/${id}/generate`, { method: "POST" }),
  finalize: (id, reviewer) =>
    request(`/releases/${id}/finalize`, { method: "POST", body: JSON.stringify({ reviewer }) }),
  finalBrief: (id) => request(`/releases/${id}/final-brief`),
  reviewStatement: (id, patch) => request(`/statements/${id}`, { method: "PUT", body: JSON.stringify(patch) }),
  compare: (from, to) => request(`/compare?from=${from}&to=${to}`),
};