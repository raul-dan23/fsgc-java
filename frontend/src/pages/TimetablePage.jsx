import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client.js';

const DAY_RO = { MONDAY: 'Luni', TUESDAY: 'Marți', WEDNESDAY: 'Miercuri', THURSDAY: 'Joi', FRIDAY: 'Vineri' };
const DAY_RO_UPPER = { MONDAY: 'LUNI', TUESDAY: 'MARŢI', WEDNESDAY: 'MIERCURI', THURSDAY: 'JOI', FRIDAY: 'VINERI' };
const WEEK = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];

export default function TimetablePage() {
  const [schedule, setSchedule] = useState([]);
  const [slots, setSlots] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [view, setView] = useState('group'); // group | room | professor
  const [filter, setFilter] = useState('');
  const [dragId, setDragId] = useState(null);
  const [overCell, setOverCell] = useState(null);
  const [toast, setToast] = useState(null);
  const [loading, setLoading] = useState(true);

  function reload() {
    setLoading(true);
    Promise.all([api.schedule(), api.timeslots(), api.rooms()]).then(([s, t, r]) => {
      setSchedule(s);
      setSlots(t);
      setRooms(r);
      setLoading(false);
    });
  }
  useEffect(reload, []);

  const roomIdByName = useMemo(() => {
    const m = {};
    rooms.forEach((r) => { m[r.name] = r.id; });
    return m;
  }, [rooms]);

  // distinct column keys for the room/professor views
  const columns = useMemo(() => {
    const set = new Set();
    schedule.forEach((a) => {
      if (!a.assigned) return;
      keysFor(a, view).forEach((k) => set.add(k));
    });
    let cols = [...set].sort();
    if (filter) cols = cols.filter((c) => c.toLowerCase().includes(filter.toLowerCase()));
    return cols;
  }, [schedule, view, filter]);

  // FSGC columns: distinct sections (program/year/specialization), sorted, grouped by an de studiu
  const sections = useMemo(() => {
    const map = new Map();
    schedule.forEach((a) => {
      if (!a.assigned) return;
      (a.sections || []).forEach((sec) => {
        const key = `${sec.program}|${sec.year}|${sec.specialization}`;
        if (!map.has(key)) map.set(key, { ...sec, key });
      });
    });
    let arr = [...map.values()].sort(cmpSection);
    if (filter) {
      const f = filter.toLowerCase();
      arr = arr.filter((s) => `${s.specialization} ${s.yearLabel}`.toLowerCase().includes(f));
    }
    return arr;
  }, [schedule, filter]);

  // contiguous runs of sections sharing the same year-label (the merged super-header)
  const yearGroups = useMemo(() => {
    const groups = [];
    sections.forEach((s) => {
      const last = groups[groups.length - 1];
      if (last && last.label === s.yearLabel) last.count += 1;
      else groups.push({ label: s.yearLabel, count: 1 });
    });
    return groups;
  }, [sections]);

  const slotsByDay = useMemo(() => {
    const m = {};
    WEEK.forEach((d) => { m[d] = []; });
    slots.forEach((s) => { if (m[s.dayOfWeek]) m[s.dayOfWeek].push(s); });
    Object.values(m).forEach((arr) => arr.sort((a, b) => a.slotIndex - b.slotIndex));
    return m;
  }, [slots]);

  const unplaced = schedule.filter((a) => !a.assigned);

  async function drop(slotId, columnKey) {
    if (dragId == null) return;
    const act = schedule.find((a) => a.id === dragId);
    setOverCell(null);
    const roomId = view === 'room' ? roomIdByName[columnKey] : (act.room ? roomIdByName[act.room] : null);
    try {
      const res = await api.move(dragId, slotId, roomId ?? null);
      const v = res.violations;
      setToast(v.length === 0
        ? { ok: true, msg: 'Mutare aplicată, fără încălcări.' }
        : { ok: false, msg: 'Mutare aplicată cu încălcări: ' + v.join('; ') });
      reload();
    } catch (e) {
      setToast({ ok: false, msg: e.message });
    }
    setDragId(null);
    setTimeout(() => setToast(null), 6000);
  }

  function actsAt(slotId, sec) {
    return schedule.filter((a) => a.assigned && a.timeSlotId === slotId
      && (a.sections || []).some((x) => x.program === sec.program
        && x.year === sec.year && x.specialization === sec.specialization));
  }

  return (
    <div>
      <h1>Orar</h1>
      <div className="panel">
        <div className="row">
          <div className="field">
            <label>Vizualizare</label>
            <select value={view} onChange={(e) => setView(e.target.value)}>
              <option value="group">Pe An / Grupă</option>
              <option value="room">Pe Sală</option>
              <option value="professor">Pe Cadru Didactic</option>
            </select>
          </div>
          <div className="field">
            <label>Filtru coloane</label>
            <input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="ex. RISE" />
          </div>
          <a href={api.exportUrl}><button className="secondary">Export Excel</button></a>
          <button className="secondary" onClick={reload}>Reîncarcă</button>
        </div>
        <p className="muted">
          Trage o activitate într-o altă celulă pentru a o muta. Mutarea e aplicată chiar dacă încalcă o
          constrângere hard (avertisment afișat), conform cerinței de forțare manuală.
        </p>
        {toast && <p className={`badge ${toast.ok ? 'ok' : 'bad'}`}>{toast.msg}</p>}
      </div>

      {unplaced.length > 0 && (
        <div className="panel">
          <h2>Neplasate ({unplaced.length})</h2>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {unplaced.map((a) => (
              <div key={a.id} className="cell-act violation" draggable
                   onDragStart={() => setDragId(a.id)}>
                <div className="t">{a.subjectCode} · {a.activityType}</div>
                <div className="s">{(a.groups || []).join(', ')}</div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="panel" style={{ overflowX: 'auto' }}>
        {loading ? <p className="muted">Se încarcă…</p>
          : view === 'group' && sections.length > 0 ? renderFsgc()
          : renderGeneric()}
        {!loading && view === 'group' && sections.length === 0 && schedule.some((a) => a.assigned) && (
          <p className="muted">
            Format FSGC indisponibil (lipsesc datele de secție/an din API). Reconstruiește și repornește
            backend-ul ca să apară coloanele pe specializare-an; momentan se afișează grila simplă.
          </p>
        )}
      </div>
    </div>
  );

  // FSGC weekly grid: days as row-blocks, specialization-year columns, room on the row below.
  function renderFsgc() {
    return (
      <table>
        <thead>
          <tr>
            <th className="slot-head" rowSpan={2}>Zi</th>
            <th className="slot-head" rowSpan={2}>Interval</th>
            {yearGroups.map((g, i) => (
              <th key={g.label + i} colSpan={g.count} style={{ textAlign: 'center' }}>{g.label}</th>
            ))}
          </tr>
          <tr>
            {sections.map((s) => <th key={s.key} className="grid-cell" style={{ textAlign: 'center' }}>{s.specialization}</th>)}
          </tr>
        </thead>
        <tbody>
          {WEEK.flatMap((day) => {
            const ds = slotsByDay[day] || [];
            return ds.flatMap((slot, idx) => {
              const cellId = (sec) => `${slot.id}|${sec.key}`;
              return [
                <tr key={`${slot.id}-a`}>
                  {idx === 0 && (
                    <td className="slot-head" rowSpan={ds.length * 2} style={{ verticalAlign: 'middle', textAlign: 'center' }}>
                      {DAY_RO_UPPER[day]}
                    </td>
                  )}
                  <td className="slot-head">{slot.startTime}–{slot.endTime}</td>
                  {sections.map((sec) => {
                    const acts = actsAt(slot.id, sec);
                    return (
                      <td key={sec.key} className="grid-cell"
                          style={{ background: overCell === cellId(sec) ? '#eef3fe' : undefined }}
                          onDragOver={(e) => { e.preventDefault(); setOverCell(cellId(sec)); }}
                          onDragLeave={() => setOverCell(null)}
                          onDrop={() => drop(slot.id, sec.specialization)}>
                        {acts.map((a) => (
                          <div key={a.id} className="cell-act" draggable
                               onDragStart={() => setDragId(a.id)}
                               title={`${a.subject} · ${a.professor || ''} · ${a.room}`}>
                            <div className="t">{a.subject}</div>
                            <div className="s">{a.professor || ''} · {abbrev(a.activityType)}</div>
                          </div>
                        ))}
                      </td>
                    );
                  })}
                </tr>,
                <tr key={`${slot.id}-s`}>
                  <td className="slot-head">Sala</td>
                  {sections.map((sec) => {
                    const acts = actsAt(slot.id, sec);
                    return (
                      <td key={sec.key} className="grid-cell">
                        {acts.map((a) => a.room).filter(Boolean).join(' / ')}
                      </td>
                    );
                  })}
                </tr>,
              ];
            });
          })}
        </tbody>
      </table>
    );
  }

  // generic grid for "Pe Sală" / "Pe Cadru Didactic": flat slot rows x columns
  function renderGeneric() {
    return (
      <table>
        <thead>
          <tr>
            <th className="slot-head">Zi / Modul</th>
            {columns.map((c) => <th key={c} className="grid-cell">{c}</th>)}
          </tr>
        </thead>
        <tbody>
          {slots.map((s) => (
            <tr key={s.id}>
              <td className="slot-head">{DAY_RO[s.dayOfWeek]} M{s.slotIndex}<br /><span className="muted">{s.startTime}</span></td>
              {columns.map((c) => {
                const cellId = `${s.id}|${c}`;
                const acts = schedule.filter((a) => a.assigned && a.timeSlotId === s.id && keysFor(a, view).includes(c));
                return (
                  <td key={c} className="grid-cell"
                      style={{ background: overCell === cellId ? '#eef3fe' : undefined }}
                      onDragOver={(e) => { e.preventDefault(); setOverCell(cellId); }}
                      onDragLeave={() => setOverCell(null)}
                      onDrop={() => drop(s.id, c)}>
                    {acts.map((a) => (
                      <div key={a.id} className="cell-act" draggable
                           onDragStart={() => setDragId(a.id)}
                           title={`${a.subject} · ${a.professor || ''} · ${a.room}`}>
                        <div className="t">{a.subjectCode} · {a.activityType}</div>
                        <div className="s">{secondary(a, view)}</div>
                      </div>
                    ))}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    );
  }
}

function cmpSection(a, b) {
  if (a.program !== b.program) return a.program < b.program ? -1 : 1; // LICENSE before MASTER
  if (a.year !== b.year) return a.year - b.year;
  return a.specialization.localeCompare(b.specialization);
}

function abbrev(type) {
  if (type === 'COURSE') return 'c';
  if (type === 'SEMINAR') return 's';
  if (type === 'LAB') return 'l';
  return type;
}

function keysFor(a, view) {
  if (view === 'group') return a.groups && a.groups.length ? a.groups : ['(fără grupă)'];
  if (view === 'room') return [a.room];
  return [a.professor || '(fără profesor)'];
}

function secondary(a, view) {
  if (view === 'group') return `${a.professor || ''} · ${a.room}`;
  if (view === 'room') return (a.groups || []).join(', ');
  return `${a.room} · ${(a.groups || []).join(', ')}`;
}
