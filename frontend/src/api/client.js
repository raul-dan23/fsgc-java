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
  async suggestedBudget() {
    return (await json(await fetch('/api/timetable/suggested-budget'))).body;
  },
  async unassignedDetails() {
    return (await json(await fetch('/api/timetable/unassigned'))).body;
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
  /** Scoate toate orele din grilă. Regulile, ponderile și datele rămân neatinse. */
  async clearSchedule() {
    const res = await fetch('/api/data/schedule/clear', { method: 'POST' });
    const { ok, body } = await json(res);
    if (!ok) {
      throw new Error((body && body.message) || 'Golirea a eșuat.');
    }
    return body;
  },
  /** Fixează ora acolo unde e (sau o eliberează), ca generarea să nu o mai mute. */
  async setPinned(activityId, pinned) {
    const res = await fetch(`/api/data/activities/${activityId}/pin`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pinned }),
    });
    const { ok, body } = await json(res);
    if (!ok) {
      throw new Error((body && body.message) || 'Fixarea a eșuat.');
    }
    return body;
  },
  async move(activityId, timeSlotId, roomId) {
    const res = await fetch(`/api/data/activities/${activityId}/assignment`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ timeSlotId, roomId }),
    });
    return (await json(res)).body;
  },

  // --- admin (CRUD pe datele de bază) ---
  // kind: 'groups' | 'professors' | 'rooms' | 'subjects' | 'activities'
  async adminSummary() {
    return (await json(await fetch('/api/admin/summary'))).body;
  },
  async subjects() {
    return (await json(await fetch('/api/data/subjects'))).body;
  },
  async activities() {
    return (await json(await fetch('/api/admin/activities'))).body;
  },
  async adminCreate(kind, payload) {
    const res = await fetch(`/api/admin/${kind}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return (await json(res)).body;
  },
  async adminUpdate(kind, id, payload) {
    const res = await fetch(`/api/admin/${kind}/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return (await json(res)).body;
  },
  /** Trece toate orele unui cadru didactic pe online (sau le aduce înapoi în săli). */
  async setProfessorOnline(professorId, online) {
    const res = await fetch(`/api/admin/professors/${professorId}/online`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ online }),
    });
    const { ok, body } = await json(res);
    if (!ok) {
      throw new Error((body && body.message) || 'Operația a eșuat.');
    }
    return body;
  },
  async adminDelete(kind, id) {
    await json(await fetch(`/api/admin/${kind}/${id}`, { method: 'DELETE' }));
  },
  /** Wipes every row of one kind. Rooms are deliberately not deletable in bulk. */
  async adminDeleteAll(kind) {
    const res = await fetch(`/api/admin/${kind}`, { method: 'DELETE' });
    const { ok, body } = await json(res);
    if (!ok) {
      throw new Error((body && body.message) || 'Ștergerea a eșuat.');
    }
    return body;
  },

  // --- orare salvate (istoric) ---
  async savedTimetables() {
    return (await json(await fetch('/api/saved-timetables'))).body;
  },
  async saveTimetable(payload) {
    const res = await fetch('/api/saved-timetables', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return (await json(res)).body;
  },
  async renameTimetable(id, payload) {
    const res = await fetch(`/api/saved-timetables/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return (await json(res)).body;
  },
  async restoreTimetable(id) {
    const res = await fetch(`/api/saved-timetables/${id}/restore`, { method: 'POST' });
    return (await json(res)).body;
  },
  async deleteSavedTimetable(id) {
    await json(await fetch(`/api/saved-timetables/${id}`, { method: 'DELETE' }));
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
  /** Creează dintr-un foc toate perechile (audiență x interval). Ce există deja e sărit. */
  async addSpecialBlocksBulk(payload) {
    const res = await fetch('/api/rules/special-blocks/bulk', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const { ok, body } = await json(res);
    if (!ok) {
      throw new Error((body && body.message) || 'Adăugarea a eșuat.');
    }
    return body;
  },
  async deleteSpecialBlocks(ids) {
    const res = await fetch('/api/rules/special-blocks', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(ids),
    });
    const { ok, body } = await json(res);
    if (!ok) {
      throw new Error((body && body.message) || 'Ștergerea a eșuat.');
    }
    return body;
  },

  exportUrl: '/api/export/excel',
};
