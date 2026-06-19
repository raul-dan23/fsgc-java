// Thin REST client for the orar backend. All paths are proxied to :8080 in dev.

async function json(res) {
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok && res.status !== 422) {
    const msg = body && body.message ? body.message : res.statusText;
    throw new Error(`${res.status} ${msg}`);
  }
  return { ok: res.ok, status: res.status, body };
}

export const api = {
  // --- import ---
  async importExcel(file) {
    const fd = new FormData();
    fd.append('file', file);
    const res = await fetch('/api/import/excel', { method: 'POST', body: fd });
    return json(res);
  },

  // --- timetable ---
  async generate(terminationSeconds) {
    const res = await fetch('/api/timetable/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ terminationSeconds }),
    });
    return (await json(res)).body;
  },
  async status(jobId) {
    const res = await fetch(`/api/timetable/status/${jobId}`);
    return (await json(res)).body;
  },
  async result(jobId) {
    const res = await fetch(`/api/timetable/result/${jobId}`);
    return (await json(res)).body;
  },
  async compare(budgets) {
    const res = await fetch('/api/timetable/compare', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ budgets }),
    });
    return (await json(res)).body;
  },

  // --- data ---
  async schedule() {
    return (await json(await fetch('/api/data/schedule'))).body;
  },
  async rooms() {
    return (await json(await fetch('/api/data/rooms'))).body;
  },
  async timeslots() {
    return (await json(await fetch('/api/data/timeslots'))).body;
  },
  async groups() {
    return (await json(await fetch('/api/data/groups'))).body;
  },
  async professors() {
    return (await json(await fetch('/api/data/professors'))).body;
  },
  async move(activityId, timeSlotId, roomId) {
    const res = await fetch(`/api/data/activities/${activityId}/assignment`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ timeSlotId, roomId }),
    });
    return (await json(res)).body;
  },

  // --- weights ---
  async getWeights() {
    return (await json(await fetch('/api/weights'))).body;
  },
  async saveWeights(w) {
    const res = await fetch('/api/weights', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(w),
    });
    return (await json(res)).body;
  },

  // --- rules ---
  async listRule(kind) {
    return (await json(await fetch(`/api/rules/${kind}`))).body;
  },
  async addRule(kind, payload) {
    const res = await fetch(`/api/rules/${kind}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return (await json(res)).body;
  },
  async deleteRule(kind, id) {
    await fetch(`/api/rules/${kind}/${id}`, { method: 'DELETE' });
  },

  exportUrl: '/api/export/excel',
};
