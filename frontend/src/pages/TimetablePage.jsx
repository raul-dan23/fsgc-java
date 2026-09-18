import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/client.js';

const DAY_RO = { MONDAY: 'Luni', TUESDAY: 'Marți', WEDNESDAY: 'Miercuri', THURSDAY: 'Joi', FRIDAY: 'Vineri' };
const DAY_RO_UPPER = { MONDAY: 'LUNI', TUESDAY: 'MARŢI', WEDNESDAY: 'MIERCURI', THURSDAY: 'JOI', FRIDAY: 'VINERI' };
const WEEK = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
const CATEGORY_RO = { DPPD: 'DPPD', CCOC: 'CCOC', DCT: 'DCT', LIMBI_STRAINE: 'Limbi străine' };

/**
 * „MD II_gr.2" -> „2"; null pentru o grupă care e tot anul („J II", „MMRPCD2").
 * Fără \b înaintea lui „gr": grupele se cheamă „J I_gr.1", iar „_" e caracter de cuvânt.
 */
const groupNumber = (name) => {
  const m = /gr(?:upa)?\.?\s*(\d+)\s*$/i.exec(String(name || '').trim());
  return m ? m[1] : null;
};

/** SI / SP; nimic pentru o oră care se ține în fiecare săptămână. */
const parityLabel = (a) => (a.weekParity === 'ODD_WEEKS' ? 'SI'
  : a.weekParity === 'EVEN_WEEKS' ? 'SP' : '');

/** Unde se ține ora: sala, sau ONLINE pentru activitățile care nu ocupă nicio sală. */
const roomLabel = (a) => (a.room ? a.room : (a.online ? 'ONLINE' : '—'));

/** Same column key the FSGC grid builds its section headers from. */
const sectionKeyOf = (g) => `${g.studyProgram}|${g.year}|${g.specialization}`;

const ZOOM_MIN = 0.3;
const ZOOM_MAX = 1.8;
const clampZoom = (z) => Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, Math.round(z * 100) / 100));

export default function TimetablePage() {
  const [schedule, setSchedule] = useState([]);
  const [slots, setSlots] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [blocks, setBlocks] = useState([]); // blocaje pe interval (DPPD/CCOC/DCT/limbi)
  const [groups, setGroups] = useState([]); // audiența blocajelor pe specializare + an
  const [view, setView] = useState('group'); // group | room | professor
  const [filter, setFilter] = useState('');
  const [dragId, setDragId] = useState(null);
  const [overCell, setOverCell] = useState(null);
  const [toast, setToast] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [busyPin, setBusyPin] = useState(false);
  const [zoom, setZoom] = useState(1);

  const viewportRef = useRef(null);
  const canvasRef = useRef(null);

  function reload() {
    setLoading(true);
    setLoadError(null);
    Promise.all([api.schedule(), api.timeslots(), api.rooms(), api.groups(),
      api.listRule('special-blocks')]).then(([s, t, r, g, b]) => {
      setSchedule(s);
      setSlots(t);
      setRooms(r);
      setGroups(g || []);
      setBlocks(b || []);
      setLoading(false);
    }).catch((e) => {
      // Fără asta pagina rămânea agățată în „Se încarcă…” la nesfârșit dacă o singură cerere
      // pica (backend repornit, de exemplu) — fără niciun mesaj și fără cale de revenire.
      setLoadError(e.message || 'Backendul nu răspunde.');
      setLoading(false);
    });
  }
  useEffect(reload, []);

  // Ctrl/⌘ + wheel to zoom (native non-passive listener so we can preventDefault).
  useEffect(() => {
    const vp = viewportRef.current;
    if (!vp) return;
    const onWheel = (e) => {
      if (!(e.ctrlKey || e.metaKey)) return;
      e.preventDefault();
      setZoom((z) => clampZoom(z - e.deltaY * 0.0015));
    };
    vp.addEventListener('wheel', onWheel, { passive: false });
    return () => vp.removeEventListener('wheel', onWheel);
  }, [loading, view]);

  function fitWidth() {
    const vp = viewportRef.current;
    const cv = canvasRef.current;
    if (!vp || !cv) return;
    const natural = cv.getBoundingClientRect().width / zoom; // undo current zoom
    if (!natural) return;
    setZoom(clampZoom((vp.clientWidth - 6) / natural));
  }

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
  const pinnedActs = schedule.filter((a) => a.pinned && a.assigned)
    .sort((x, y) => WEEK.indexOf(x.day) - WEEK.indexOf(y.day) || x.slotIndex - y.slotIndex);

  /** Fixează / eliberează o oră. Fixată, generarea o lasă exact acolo unde e. */
  async function togglePin(a) {
    try {
      const res = await api.setPinned(a.id, !a.pinned);
      setSchedule((cur) => cur.map((x) => (x.id === a.id ? { ...x, pinned: res.activity.pinned } : x)));
      if (!res.activity.pinned) {
        setToast({ ok: true, msg: `„${a.subject}" nu mai e fixată.` });
      } else if (res.violations.length === 0) {
        setToast({ ok: true, msg: `„${a.subject}" e fixată — generarea n-o mai mută.` });
      } else {
        // Fixată peste o regulă încălcată: solverul nu o mai poate repara, deci se spune acum.
        setToast({ ok: false, msg: `„${a.subject}" e fixată aici, dar locul încalcă: `
          + res.violations.join('; ') + '. Generarea nu va putea repara asta.' });
      }
    } catch (e) {
      setToast({ ok: false, msg: e.message });
    }
    setTimeout(() => setToast(null), 5000);
  }

  async function drop(slotId, columnKey, blocked) {
    if (dragId == null) return;
    if (blocked && blocked.length > 0) {
      setOverCell(null);
      setDragId(null);
      setToast({ ok: false, msg: `Interval blocat (${blockLabel(blocked)}) — aici nu se poate plasa nimic.` });
      setTimeout(() => setToast(null), 6000);
      return;
    }
    const act = schedule.find((a) => a.id === dragId);
    setOverCell(null);
    const roomId = act.online ? null
      : view === 'room' ? roomIdByName[columnKey]
      : (act.room ? roomIdByName[act.room] : null);
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

  /**
   * slotId -> blocajele care cad pe el, fiecare cu audiența rezolvată în chei de secție și
   * nume de grupe, ca să putem colora exact celulele afectate din grilă.
   */
  const blockIndex = useMemo(() => {
    const m = new Map();
    blocks.forEach((r) => {
      const slotId = r.timeSlot && r.timeSlot.id;
      if (!slotId) return;
      let audience = [];
      if (r.studentGroup) {
        audience = [r.studentGroup];
      } else if (r.specialization && r.year != null) {
        audience = groupsOfSpec(String(r.specialization).toLowerCase(), r.year);
      }
      if (audience.length === 0) return; // audiență incompletă — regula nu se aplică la nimic
      const entry = {
        category: r.category,
        // o regulă pe grupă colorează coloana secției, deci spunem pe cine anume vizează
        scope: r.studentGroup ? r.studentGroup.name : null,
        sections: new Set(audience.map(sectionKeyOf)),
        groups: new Set(audience.map((g) => g.name)),
      };
      if (!m.has(slotId)) m.set(slotId, []);
      m.get(slotId).push(entry);
    });
    return m;
  }, [blocks, groups]);

  const groupByName = useMemo(() => new Map(groups.map((g) => [g.name, g])), [groups]);

  function groupsOfSpec(specLower, year) {
    return groups.filter((g) => g.specialization
      && g.specialization.toLowerCase() === specLower && g.year === year);
  }

  /**
   * „gr. 1" / „gr. 1, 2" pentru celula unei secții: doar grupele acelei secții, fiindcă un curs
   * comun apare în mai multe coloane, iar numerele altei secții n-ar spune nimic acolo. Gol când
   * ora e pentru tot anul — atunci coloana spune deja totul.
   */
  function groupLabel(a, sec) {
    const names = (a.groups || []).filter((n) => {
      const g = groupByName.get(n);
      return !sec || !g || (g.studyProgram === sec.program && g.year === sec.year
        && g.specialization === sec.specialization);
    });
    const numbers = names.map(groupNumber).filter(Boolean);
    return numbers.length ? `gr. ${[...new Set(numbers)].join(', ')}` : '';
  }

  /** Blocajele care acoperă o celulă — cheia e o secție (grila FSGC) sau o grupă (grila simplă). */
  function blocksAt(slotId, key, byGroup) {
    const list = blockIndex.get(slotId);
    if (!list) return [];
    return list.filter((b) => (byGroup ? b.groups.has(key) : b.sections.has(key)));
  }

  const blockLabel = (bs) => [...new Set(bs.map((b) => (CATEGORY_RO[b.category] || b.category)
    + (b.scope ? ` · ${b.scope}` : '')))].join(' / ');
  /** Intrusă = o activitate a cărei grupă e blocată aici și care nu e din categoria rezervată. */
  const intrudes = (a, bs) => bs.some((b) => b.category !== a.specialCategory
    && (a.groups || []).some((g) => b.groups.has(g)));

  function actsAt(slotId, sec) {
    return schedule.filter((a) => a.assigned && a.timeSlotId === slotId
      && (a.sections || []).some((x) => x.program === sec.program
        && x.year === sec.year && x.specialization === sec.specialization));
  }

  return (
    <div>
      <h1>Orar</h1>

      <div className="panel">
        <div className="tt-toolbar">
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

          <div className="field">
            <label>Zoom</label>
            <div className="zoom">
              <button title="Micșorează (Ctrl + scroll)" onClick={() => setZoom((z) => clampZoom(z - 0.1))}>−</button>
              <span className="val" title="Revino la 100%" onClick={() => setZoom(1)}>{Math.round(zoom * 100)}%</span>
              <button title="Mărește (Ctrl + scroll)" onClick={() => setZoom((z) => clampZoom(z + 0.1))}>+</button>
              <span className="divider" />
              <button className="fit" title="Potrivește lățimea pe ecran" onClick={fitWidth}>Potrivește</button>
            </div>
          </div>

          <span className="grow" />
          <a href={api.exportUrl}><button className="secondary">Export Excel</button></a>
          <button className="secondary" onClick={reload}>Reîncarcă</button>
        </div>

        <div className="legend" style={{ marginTop: 14 }}>
          <span><span className="dot" style={{ background: '#2f5fe0' }} />activitate (trage pentru a muta)</span>
          <span><span className="dot" style={{ background: '#c1352b' }} />conflict / neplasată</span>
          <span><span className="dot blocked-dot" />interval blocat (nu se alocă nimic)</span>
          <span><span className="dot" style={{ background: '#0f766e' }} />online (fără sală)</span>
          <span>🔒 oră fixată — generarea n-o mută</span>
          <span>c = curs · s = seminar · l = laborator</span>
        </div>
        <p className="hint">
          Trage o activitate în altă celulă pentru a o muta — mutarea se aplică chiar dacă încalcă o
          constrângere hard (primești avertisment). Pune-i lacătul (🔒) dacă vrei ca generarea să o
          lase exact acolo: restul orarului se va construi în jurul ei. Folosește <b>Potrivește</b> sau <b>Ctrl + scroll</b> ca
          să vezi tot orarul dintr-o privire.
        </p>
      </div>

      {pinnedActs.length > 0 && (
        <div className="panel">
          <h2>Ore fixate ({pinnedActs.length})</h2>
          <p className="muted" style={{ marginTop: -6 }}>
            La următoarea generare rămân exact unde sunt, iar restul orarului se construiește în
            jurul lor. Click pe lacăt ca să eliberezi una.
          </p>
          <div className="chips">
            {pinnedActs.map((a) => (
              <button key={a.id} type="button" className="chip on" disabled={busyPin}
                      title="Click pentru a elibera ora"
                      onClick={() => togglePin(a)}>
                🔒 {a.subject} · {(a.groups || []).join(', ')} · {DAY_RO[a.day] || a.day} M{a.slotIndex}
              </button>
            ))}
          </div>
        </div>
      )}

      {unplaced.length > 0 && (
        <div className="panel">
          <h2>Neplasate ({unplaced.length})</h2>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {unplaced.map((a) => (
              <div key={a.id} className="cell-act violation" draggable
                   onDragStart={() => setDragId(a.id)}>
                <div className="t">
                  {a.subjectCode} · {abbrev(a.activityType)}
                  {parityLabel(a) && <span className="parity">{parityLabel(a)}</span>}
                </div>
                <div className="s">
                  {[a.professor, (a.groups || []).join(', ')].filter(Boolean).join(' · ')}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {loadError && (
        <div className="panel">
          <p className="badge bad" style={{ display: 'inline-block' }}>
            Nu s-au putut încărca datele: {loadError}
          </p>
          <p className="muted" style={{ marginBottom: 10 }}>
            Verifică dacă backendul rulează, apoi încearcă din nou.
          </p>
          <button onClick={reload}>Încearcă din nou</button>
        </div>
      )}

      <div className="panel">
        <div className="tt-viewport" ref={viewportRef}>
          <div className="tt-canvas" ref={canvasRef} style={{ zoom }}>
            {loading ? <p className="muted" style={{ padding: 16 }}>Se încarcă…</p>
              : view === 'group' && sections.length > 0 ? renderFsgc()
              : renderGeneric()}
          </div>
        </div>
        {!loading && view === 'group' && sections.length === 0 && schedule.some((a) => a.assigned) && (
          <p className="hint">
            Format FSGC indisponibil (lipsesc datele de secție/an din API). Reconstruiește și repornește
            backend-ul ca să apară coloanele pe specializare-an; momentan se afișează grila simplă.
          </p>
        )}
      </div>

      {toast && <div className={`toast ${toast.ok ? 'ok' : 'bad'}`}>{toast.msg}</div>}
    </div>
  );

  // FSGC weekly grid: days as row-blocks, specialization-year columns, room on the row below.
  function renderFsgc() {
    return (
      <table className="tt-table">
        <thead>
          <tr>
            <th className="tt-col-day" rowSpan={2}>Zi</th>
            <th className="tt-col-time" rowSpan={2}>Interval</th>
            {yearGroups.map((g, i) => (
              <th key={g.label + i} colSpan={g.count}>{g.label}</th>
            ))}
          </tr>
          <tr>
            {sections.map((s) => <th key={s.key} className="tt-sec">{s.specialization}</th>)}
          </tr>
        </thead>
        <tbody>
          {WEEK.flatMap((day) => {
            const ds = slotsByDay[day] || [];
            return ds.flatMap((slot, idx) => {
              const cellId = (sec) => `${slot.id}|${sec.key}`;
              const firstOfDay = idx === 0;
              return [
                <tr key={`${slot.id}-a`} className={firstOfDay ? 'tt-rowsep' : undefined}>
                  {firstOfDay && (
                    <td className="tt-col-day daysep" rowSpan={ds.length * 2}
                        style={{ verticalAlign: 'middle' }}>
                      {DAY_RO_UPPER[day]}
                    </td>
                  )}
                  <td className="tt-col-time">{slot.startTime}–{slot.endTime}</td>
                  {sections.map((sec) => {
                    const acts = actsAt(slot.id, sec);
                    const blocked = blocksAt(slot.id, sec.key);
                    const isBlocked = blocked.length > 0;
                    return (
                      <td key={sec.key}
                          className={`grid-cell${isBlocked ? ' blocked' : ''}${overCell === cellId(sec) ? ' over' : ''}`}
                          title={isBlocked ? `Interval blocat pentru ${blockLabel(blocked)}` : undefined}
                          onDragOver={(e) => {
                            if (isBlocked) return; // fără preventDefault → drop refuzat de browser
                            e.preventDefault();
                            setOverCell(cellId(sec));
                          }}
                          onDragLeave={() => setOverCell(null)}
                          onDrop={() => drop(slot.id, sec.specialization, blocked)}>
                        {isBlocked && <div className="cell-block">{blockLabel(blocked)}</div>}
                        {acts.map((a) => (
                          <div key={a.id}
                               className={`cell-act${isBlocked && intrudes(a, blocked) ? ' violation' : ''}`
                                 + (a.online ? ' online' : '') + (a.pinned ? ' pinned' : '')}
                               draggable
                               onDragStart={() => setDragId(a.id)}
                               title={[a.subject, a.professor, abbrev(a.activityType),
                                 groupLabel(a, sec), parityLabel(a), roomLabel(a)]
                                 .filter(Boolean).join(' · ')}>
                            <div className="t">
                              {a.subject}
                              <PinButton act={a} onToggle={togglePin} />
                            </div>
                            <div className="s">
                              {[a.professor, abbrev(a.activityType), groupLabel(a, sec)]
                                .filter(Boolean).join(' · ')}
                              {parityLabel(a) && <span className="parity">{parityLabel(a)}</span>}
                            </div>
                          </div>
                        ))}
                      </td>
                    );
                  })}
                </tr>,
                <tr key={`${slot.id}-s`}>
                  <td className="tt-col-time tt-room">Sala</td>
                  {sections.map((sec) => {
                    const acts = actsAt(slot.id, sec);
                    const isBlocked = blocksAt(slot.id, sec.key).length > 0;
                    return (
                      <td key={sec.key} className={`grid-cell tt-room${isBlocked ? ' blocked' : ''}`}>
                        {acts.map((a) => (a.online
                          ? <span key={a.id} className="online-tag">ONLINE</span>
                          : <span key={a.id}>{a.room}</span>))
                          .reduce((out, el) => (out.length ? [...out, ' / ', el] : [el]), [])}
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
      <table className="tt-table">
        <thead>
          <tr>
            <th className="tt-col-first">Zi / Modul</th>
            {columns.map((c) => <th key={c} className="tt-sec">{c}</th>)}
          </tr>
        </thead>
        <tbody>
          {slots.map((s) => (
            <tr key={s.id} className={s.slotIndex === 1 ? 'tt-rowsep' : undefined}>
              <td className="tt-col-first">
                {DAY_RO[s.dayOfWeek]} M{s.slotIndex}<br /><span className="tt-room">{s.startTime}</span>
              </td>
              {columns.map((c) => {
                const cellId = `${s.id}|${c}`;
                const acts = schedule.filter((a) => a.assigned && a.timeSlotId === s.id && keysFor(a, view).includes(c));
                // blocajele au audiență pe grupe, deci se pot marca doar în vizualizarea pe grupă
                const blocked = view === 'group' ? blocksAt(s.id, c, true) : [];
                const isBlocked = blocked.length > 0;
                return (
                  <td key={c}
                      className={`grid-cell${isBlocked ? ' blocked' : ''}${overCell === cellId ? ' over' : ''}`}
                      title={isBlocked ? `Interval blocat pentru ${blockLabel(blocked)}` : undefined}
                      onDragOver={(e) => {
                        if (isBlocked) return;
                        e.preventDefault();
                        setOverCell(cellId);
                      }}
                      onDragLeave={() => setOverCell(null)}
                      onDrop={() => drop(s.id, c, blocked)}>
                    {isBlocked && <div className="cell-block">{blockLabel(blocked)}</div>}
                    {acts.map((a) => (
                      <div key={a.id}
                           className={`cell-act${isBlocked && intrudes(a, blocked) ? ' violation' : ''}`
                             + (a.online ? ' online' : '') + (a.pinned ? ' pinned' : '')}
                           draggable
                           onDragStart={() => setDragId(a.id)}
                           title={[a.subject, a.professor, abbrev(a.activityType),
                             groupLabel(a), parityLabel(a), roomLabel(a)]
                             .filter(Boolean).join(' · ')}>
                        <div className="t">
                          {a.subjectCode} · {abbrev(a.activityType)}
                          {parityLabel(a) && <span className="parity">{parityLabel(a)}</span>}
                          <PinButton act={a} onToggle={togglePin} />
                        </div>
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

/** Lacătul de pe o oră: fixată, generarea n-o mai mută. */
function PinButton({ act, onToggle }) {
  return (
    <button type="button" className={`pin${act.pinned ? ' on' : ''}`}
            title={act.pinned ? 'Fixată — click pentru a elibera'
              : 'Fixează ora aici, ca generarea să n-o mute'}
            onClick={(e) => { e.stopPropagation(); onToggle(act); }}
            onDragStart={(e) => e.preventDefault()}>
      {act.pinned ? '🔒' : '🔓'}
    </button>
  );
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
  if (view === 'room') return [roomLabel(a)];
  return [a.professor || '(fără profesor)'];
}

function secondary(a, view) {
  if (view === 'group') return `${a.professor || ''} · ${roomLabel(a)}`;
  if (view === 'room') return (a.groups || []).join(', ');
  return `${roomLabel(a)} · ${(a.groups || []).join(', ')}`;
}
