import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client.js';

/**
 * History of timetable snapshots. "Salvează" copies the current placement of every activity;
 * "Restaurează" writes a snapshot back over the live timetable.
 */
export default function SavedTimetablesPage() {
  const [rows, setRows] = useState(null);
  const [current, setCurrent] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);
  const [name, setName] = useState('');
  const [note, setNote] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [draft, setDraft] = useState({ name: '', note: '' });

  const reload = useCallback(() => {
    api.savedTimetables().then(setRows).catch((e) => { setRows([]); setError(e.message); });
    api.activities().then(setCurrent).catch(() => setCurrent(null));
  }, []);

  useEffect(() => { reload(); }, [reload]);

  function flash(msg) {
    setNotice(msg);
    setTimeout(() => setNotice(null), 4000);
  }

  async function save() {
    setBusy(true); setError(null);
    try {
      const r = await api.saveTimetable({ name, note });
      flash(`Salvat: „${r.name}” (${r.assignedCount} din ${r.totalActivities} activități plasate).`);
      setName(''); setNote('');
      reload();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  async function restore(row) {
    if (!window.confirm(
      `Restaurezi „${row.name}”?\n\nOrarul curent va fi înlocuit. Dacă vrei să-l păstrezi, salvează-l întâi.`
    )) return;
    setBusy(true); setError(null);
    try {
      const r = await api.restoreTimetable(row.id);
      const extra = [];
      if (r.cleared) extra.push(`${r.cleared} lăsate neplasate`);
      if (r.skippedMissingActivity) extra.push(`${r.skippedMissingActivity} activități inexistente`);
      if (r.skippedMissingSlotOrRoom) extra.push(`${r.skippedMissingSlotOrRoom} cu sală/interval șters`);
      flash(`Restaurat: ${r.applied} activități plasate${extra.length ? ' — ' + extra.join(', ') : ''}.`);
      reload();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  async function remove(row) {
    if (!window.confirm(`Ștergi „${row.name}”? Acțiunea nu poate fi anulată.`)) return;
    setBusy(true); setError(null);
    try {
      await api.deleteSavedTimetable(row.id);
      flash('Șters.');
      reload();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  async function saveRename() {
    setBusy(true); setError(null);
    try {
      await api.renameTimetable(editingId, draft);
      setEditingId(null);
      flash('Actualizat.');
      reload();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  const assigned = current ? current.filter((a) => a.assigned).length : 0;
  const total = current ? current.length : 0;

  if (!rows) return <p className="muted">Se încarcă…</p>;

  return (
    <div>
      <h1>Orare salvate</h1>

      <div className="panel">
        <h2>Salvează orarul curent</h2>
        <p className="muted">
          {total === 0
            ? 'Nu există activități. Importă datele întâi.'
            : `Orarul curent are ${assigned} din ${total} activități plasate.`}
        </p>
        <div className="row">
          <div className="field">
            <label>Nume</label>
            <input value={name} onChange={(e) => setName(e.target.value)}
                   placeholder="ex. Varianta 1 — sem. II" style={{ width: 260 }} />
          </div>
          <div className="field">
            <label>Observații (opțional)</label>
            <input value={note} onChange={(e) => setNote(e.target.value)}
                   placeholder="ce e special la varianta asta" style={{ width: 340 }} />
          </div>
          <button onClick={save} disabled={busy || total === 0}>Salvează orarul curent</button>
        </div>
        <p className="muted" style={{ marginBottom: 0 }}>
          Dacă lași numele gol, se completează automat cu data și ora.
        </p>
      </div>

      <div className="panel">
        <h2>Istoric ({rows.length})</h2>
        {error && <p className="badge bad" style={{ display: 'inline-block' }}>{error}</p>}
        {notice && <p className="badge ok" style={{ display: 'inline-block' }}>{notice}</p>}

        {rows.length === 0 ? (
          <p className="muted">
            Niciun orar salvat încă. Generează un orar, ajustează-l dacă e nevoie, apoi salvează-l aici.
          </p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table>
              <thead>
                <tr>
                  <th style={{ minWidth: 200 }}>Nume</th>
                  <th style={{ minWidth: 150 }}>Salvat la</th>
                  <th style={{ minWidth: 110 }}>Plasate</th>
                  <th style={{ minWidth: 120 }}>Scor</th>
                  <th style={{ minWidth: 200 }}>Observații</th>
                  <th style={{ minWidth: 230 }}>Acțiuni</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  editingId === r.id ? (
                    <tr key={r.id} style={{ background: 'var(--accent-soft)' }}>
                      <td>
                        <input value={draft.name} style={{ width: '100%' }}
                               onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
                      </td>
                      <td className="muted">{fmt(r.createdAt)}</td>
                      <td>{r.assignedCount} / {r.totalActivities}</td>
                      <td className="muted">{r.score || '—'}</td>
                      <td>
                        <input value={draft.note} style={{ width: '100%' }}
                               onChange={(e) => setDraft({ ...draft, note: e.target.value })} />
                      </td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        <button onClick={saveRename} disabled={busy}>Salvează</button>{' '}
                        <button className="ghost" onClick={() => setEditingId(null)} disabled={busy}>Anulează</button>
                      </td>
                    </tr>
                  ) : (
                    <tr key={r.id}>
                      <td><b>{r.name}</b></td>
                      <td className="muted">{fmt(r.createdAt)}</td>
                      <td>
                        <span className={`badge ${r.assignedCount === r.totalActivities ? 'ok' : 'warn'}`}>
                          {r.assignedCount} / {r.totalActivities}
                        </span>
                      </td>
                      <td className="muted">{r.score || '—'}</td>
                      <td className="muted">{r.note || '—'}</td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        <button onClick={() => restore(r)} disabled={busy}>Restaurează</button>{' '}
                        <button className="ghost" disabled={busy}
                                onClick={() => { setEditingId(r.id); setDraft({ name: r.name, note: r.note || '' }); }}>
                          Redenumește
                        </button>{' '}
                        <button className="danger" onClick={() => remove(r)} disabled={busy}>Șterge</button>
                      </td>
                    </tr>
                  )
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

/** "2026-09-17T14:05:00" -> "17.09.2026 14:05" */
function fmt(iso) {
  if (!iso) return '—';
  const [d, t] = iso.split('T');
  if (!d) return iso;
  const [y, m, day] = d.split('-');
  return `${day}.${m}.${y}${t ? ' ' + t.slice(0, 5) : ''}`;
}
