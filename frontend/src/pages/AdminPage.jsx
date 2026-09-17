import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client.js';

const PROGRAMS = [['LICENSE', 'Licență'], ['MASTER', 'Master']];
const TYPOLOGIES = [['SEMINAR', 'Seminar'], ['COURSE', 'Curs'], ['LAB', 'Laborator'], ['AMPHITHEATER', 'Amfiteatru']];
const ACT_TYPES = [['COURSE', 'Curs'], ['SEMINAR', 'Seminar'], ['LAB', 'Laborator']];
const PARITIES = [['EVERY_WEEK', 'Săptămânal'], ['ODD_WEEKS', 'Săpt. impare (SI)'], ['EVEN_WEEKS', 'Săpt. pare (SP)']];
const CATEGORIES = [['NORMAL', 'Normal'], ['DCT', 'DCT'], ['DPPD', 'DPPD'], ['CCOC', 'CCOC'], ['LIMBI_STRAINE', 'Limbi străine']];

/**
 * Admin CRUD over the base data. Every tab is the same generic table driven by a `columns` spec;
 * the tab config says how to load rows, how to turn an edited row into a request body, and what a
 * brand-new row starts out as. Lookup tabs (subjects/professors/groups) are loaded once and shared
 * so the Activități tab can render selects instead of raw ids.
 */
export default function AdminPage() {
  const [tab, setTab] = useState('groups');
  const [summary, setSummary] = useState(null);
  const [lookups, setLookups] = useState({ subjects: [], professors: [], groups: [] });

  const refreshSummary = useCallback(() => {
    api.adminSummary().then(setSummary).catch(() => setSummary(null));
  }, []);

  const refreshLookups = useCallback(() => {
    Promise.all([api.subjects(), api.professors(), api.groups()])
      .then(([subjects, professors, groups]) => setLookups({ subjects, professors, groups }))
      .catch(() => {});
  }, []);

  useEffect(() => { refreshSummary(); refreshLookups(); }, [refreshSummary, refreshLookups]);

  const onChanged = useCallback(() => { refreshSummary(); refreshLookups(); }, [refreshSummary, refreshLookups]);

  const tabs = useMemo(() => buildTabs(lookups), [lookups]);
  const active = tabs.find((t) => t.kind === tab);

  return (
    <div>
      <h1>Administrare date</h1>

      <div className="panel">
        <div className="score-tiles">
          {tabs.map((t) => (
            <div className="tile" key={t.kind}>
              <div className="n">{summary ? summary[t.kind] : '—'}</div>
              <div className="l">{t.label}</div>
            </div>
          ))}
        </div>
        <p className="muted" style={{ marginBottom: 0 }}>
          Modificările se aplică direct în baza de date. După ce schimbi date care afectează orarul
          (grupe, activități, săli), regenerează orarul din <b>Generare</b>.
        </p>
        {tab === 'rooms' && (
          <p className="muted" style={{ marginBottom: 0, marginTop: 8 }}>
            O sală nouă e liberă în toate cele 40 de intervale — nu trebuie să declari când e
            disponibilă. Dacă e ocupată în anumite intervale, adaugă o <b>indisponibilitate de
            sală</b> în <b>Constrângeri</b>.
          </p>
        )}
      </div>

      <div className="panel">
        <div className="row" style={{ gap: 6, marginBottom: 16 }}>
          {tabs.map((t) => (
            <button
              key={t.kind}
              onClick={() => setTab(t.kind)}
              className={t.kind === tab ? '' : 'ghost'}
            >
              {t.label}
            </button>
          ))}
        </div>
        {active && <EntityTable key={active.kind} tab={active} onChanged={onChanged} />}
      </div>
    </div>
  );
}

/** Tab definitions. `lookups` feeds the id-based selects on the Activități tab. */
function buildTabs({ subjects, professors, groups }) {
  const subjectOptions = subjects.map((s) => [s.id, `${s.code} — ${s.name}`]);
  const professorOptions = professors.map((p) => [p.id, p.name]);

  return [
    {
      kind: 'groups',
      label: 'Grupe',
      load: api.groups,
      search: (r) => [r.name, r.specialization, r.department].join(' '),
      blank: { name: '', specialization: '', year: 1, studyProgram: 'LICENSE', studentCount: 25, department: 'FSGC' },
      payload: (r) => ({
        name: r.name, specialization: r.specialization, year: num(r.year),
        studyProgram: r.studyProgram, studentCount: num(r.studentCount), department: r.department,
      }),
      label_of: (r) => r.name,
      columns: [
        { key: 'name', label: 'Nume (cheie)', type: 'text', width: 180 },
        { key: 'specialization', label: 'Secție', type: 'text', width: 110 },
        { key: 'year', label: 'An', type: 'number', width: 70 },
        { key: 'studyProgram', label: 'Program', type: 'select', options: PROGRAMS, width: 120 },
        { key: 'studentCount', label: 'Studenți', type: 'number', width: 90 },
        { key: 'department', label: 'Departament', type: 'text', width: 120 },
      ],
    },
    {
      kind: 'professors',
      label: 'Profesori',
      load: api.professors,
      search: (r) => [r.name, r.email, r.title, r.department].join(' '),
      blank: { name: '', email: '', title: '', department: 'FSGC', hasOwnLaptop: true },
      payload: (r) => ({
        name: r.name, email: r.email, title: r.title,
        department: r.department, hasOwnLaptop: !!r.hasOwnLaptop,
      }),
      label_of: (r) => r.name,
      columns: [
        { key: 'name', label: 'Nume', type: 'text', width: 200 },
        { key: 'email', label: 'Email', type: 'text', width: 220 },
        { key: 'title', label: 'Titlu', type: 'text', width: 100 },
        { key: 'department', label: 'Departament', type: 'text', width: 120 },
        { key: 'hasOwnLaptop', label: 'Laptop propriu', type: 'checkbox', width: 110 },
      ],
    },
    {
      kind: 'rooms',
      label: 'Săli',
      load: api.rooms,
      search: (r) => [r.name, r.floor, r.typology, r.department].join(' '),
      blank: { name: '', department: 'FSGC', floor: '', capacity: 30, typology: 'SEMINAR', usageRestrictions: '' },
      payload: (r) => ({
        name: r.name, department: r.department, floor: r.floor,
        capacity: num(r.capacity), typology: r.typology, usageRestrictions: r.usageRestrictions,
      }),
      label_of: (r) => r.name,
      columns: [
        { key: 'name', label: 'Sală', type: 'text', width: 130 },
        { key: 'capacity', label: 'Capacitate', type: 'number', width: 100 },
        { key: 'typology', label: 'Tip', type: 'select', options: TYPOLOGIES, width: 130 },
        { key: 'floor', label: 'Etaj', type: 'text', width: 90 },
        { key: 'department', label: 'Departament', type: 'text', width: 110 },
        { key: 'usageRestrictions', label: 'Restricții', type: 'text', width: 160 },
        {
          // A room is usable in all 40 slots by default; only the exceptions are stored. Showing a
          // count of "available days" read 0 for every room created here and suggested the
          // opposite of the truth, so show the exceptions instead.
          key: 'unavailabilities', label: 'Indisponibilă', readOnly: true, width: 150,
          render: (r) => {
            const n = r.unavailabilities ? r.unavailabilities.length : 0;
            return n === 0
              ? <span className="muted">mereu liberă</span>
              : `${n} ${n === 1 ? 'interval' : 'intervale'}`;
          },
        },
      ],
    },
    {
      kind: 'subjects',
      label: 'Discipline',
      load: api.subjects,
      search: (r) => [r.code, r.name, r.department].join(' '),
      blank: { code: '', name: '', department: 'FSGC' },
      payload: (r) => ({ code: r.code, name: r.name, department: r.department }),
      label_of: (r) => r.name,
      columns: [
        { key: 'code', label: 'Cod', type: 'text', width: 180 },
        { key: 'name', label: 'Denumire', type: 'text', width: 320 },
        { key: 'department', label: 'Departament', type: 'text', width: 130 },
      ],
    },
    {
      kind: 'activities',
      label: 'Activități',
      load: api.activities,
      search: (r) => [r.subjectCode, r.subjectName, r.professorName, r.rawType, (r.groupNames || []).join(' ')].join(' '),
      blank: {
        subjectId: subjects[0] ? subjects[0].id : null, professorId: null, activityType: 'SEMINAR',
        weekParity: 'EVERY_WEEK', specialCategory: 'NORMAL', requiresAmphitheater: false,
        rawType: 'Seminar', durationInSlots: 1, groupIds: [], parityPairKey: null,
      },
      payload: (r) => ({
        subjectId: r.subjectId, professorId: r.professorId || null, activityType: r.activityType,
        weekParity: r.weekParity, specialCategory: r.specialCategory,
        requiresAmphitheater: !!r.requiresAmphitheater, rawType: r.rawType,
        durationInSlots: num(r.durationInSlots), groupIds: r.groupIds || [],
        // Not editable here, but must round-trip or saving would unpair an SI/SP hour.
        parityPairKey: r.parityPairKey || null,
      }),
      label_of: (r) => `${r.subjectName} (${r.activityType})`,
      columns: [
        { key: 'subjectId', label: 'Disciplină', type: 'select', options: subjectOptions, width: 260,
          render: (r) => r.subjectName },
        { key: 'professorId', label: 'Profesor', type: 'select', options: professorOptions, nullable: true, width: 180,
          render: (r) => r.professorName || <span className="muted">— niciunul —</span> },
        { key: 'groupIds', label: 'Grupe', type: 'groups', options: groups, width: 220,
          render: (r) => (r.groupNames || []).join(', ') || <span className="muted">—</span> },
        { key: 'activityType', label: 'Tip', type: 'select', options: ACT_TYPES, width: 120 },
        { key: 'weekParity', label: 'Săptămâni', type: 'select', options: PARITIES, width: 150 },
        { key: 'specialCategory', label: 'Categorie', type: 'select', options: CATEGORIES, width: 120 },
        { key: 'requiresAmphitheater', label: 'Amfi.', type: 'checkbox', width: 70 },
        { key: 'rawType', label: 'Valoare Excel', type: 'text', width: 150 },
        { key: 'students', label: 'Stud.', readOnly: true, width: 70 },
      ],
    },
  ];
}

function num(v) {
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

/** Generic view/edit table for one tab. Holds its own rows, edit buffer and error state. */
function EntityTable({ tab, onChanged }) {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const [draft, setDraft] = useState(null);
  const [creating, setCreating] = useState(false);
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState(false);

  const reload = useCallback(() => {
    setError(null);
    tab.load().then(setRows).catch((e) => { setRows([]); setError(e.message); });
  }, [tab]);

  useEffect(() => { reload(); }, [reload]);

  function startEdit(row) {
    setCreating(false);
    setEditingId(row.id);
    setDraft({ ...row });
    setError(null);
  }

  function startCreate() {
    setEditingId(null);
    setCreating(true);
    setDraft({ ...tab.blank });
    setError(null);
  }

  function cancel() {
    setEditingId(null);
    setCreating(false);
    setDraft(null);
    setError(null);
  }

  async function save() {
    setBusy(true);
    setError(null);
    try {
      const body = tab.payload(draft);
      if (creating) {
        await api.adminCreate(tab.kind, body);
        setNotice('Adăugat.');
      } else {
        await api.adminUpdate(tab.kind, editingId, body);
        setNotice('Salvat.');
      }
      cancel();
      reload();
      onChanged();
      setTimeout(() => setNotice(null), 2500);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  async function remove(row) {
    if (!window.confirm(`Ștergi „${tab.label_of(row)}”? Acțiunea nu poate fi anulată.`)) return;
    setBusy(true);
    setError(null);
    try {
      await api.adminDelete(tab.kind, row.id);
      setNotice('Șters.');
      reload();
      onChanged();
      setTimeout(() => setNotice(null), 2500);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  const filtered = useMemo(() => {
    if (!rows) return [];
    const needle = q.trim().toLowerCase();
    if (!needle) return rows;
    return rows.filter((r) => (tab.search(r) || '').toLowerCase().includes(needle));
  }, [rows, q, tab]);

  if (!rows) return <p className="muted">Se încarcă…</p>;

  return (
    <div>
      <div className="row" style={{ marginBottom: 14, alignItems: 'center' }}>
        <div className="field">
          <label>Caută</label>
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="filtrează…" style={{ width: 240 }} />
        </div>
        <button onClick={startCreate} disabled={creating}>+ Adaugă</button>
        <span className="spacer" style={{ flex: 1 }} />
        <span className="muted">{filtered.length} / {rows.length} rânduri</span>
      </div>

      {error && <p className="badge bad" style={{ display: 'inline-block', marginBottom: 12 }}>{error}</p>}
      {notice && <p className="badge ok" style={{ display: 'inline-block', marginBottom: 12 }}>{notice}</p>}

      <div style={{ overflowX: 'auto' }}>
        <table>
          <thead>
            <tr>
              {tab.columns.map((c) => <th key={c.key} style={{ minWidth: c.width }}>{c.label}</th>)}
              <th style={{ minWidth: 150 }}>Acțiuni</th>
            </tr>
          </thead>
          <tbody>
            {creating && (
              <EditRow columns={tab.columns} draft={draft} setDraft={setDraft}
                       onSave={save} onCancel={cancel} busy={busy} isNew />
            )}
            {filtered.map((row) => (
              row.id === editingId ? (
                <EditRow key={row.id} columns={tab.columns} draft={draft} setDraft={setDraft}
                         onSave={save} onCancel={cancel} busy={busy} />
              ) : (
                <tr key={row.id}>
                  {tab.columns.map((c) => <td key={c.key}>{display(row, c)}</td>)}
                  <td>
                    <button className="ghost" onClick={() => startEdit(row)} disabled={busy}>Editează</button>{' '}
                    <button className="danger" onClick={() => remove(row)} disabled={busy}>Șterge</button>
                  </td>
                </tr>
              )
            ))}
            {filtered.length === 0 && !creating && (
              <tr><td colSpan={tab.columns.length + 1} className="muted">Niciun rând.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** Read-only rendering of one cell. */
function display(row, col) {
  if (col.render) return col.render(row);
  const v = row[col.key];
  if (typeof v === 'boolean') return v ? 'da' : 'nu';
  if (v === null || v === undefined || v === '') return <span className="muted">—</span>;
  if (col.type === 'select' && Array.isArray(col.options)) {
    const hit = col.options.find(([value]) => String(value) === String(v));
    if (hit) return hit[1];
  }
  return String(v);
}

/** One row switched into edit mode: an input per editable column. */
function EditRow({ columns, draft, setDraft, onSave, onCancel, busy, isNew }) {
  const set = (key, value) => setDraft({ ...draft, [key]: value });

  return (
    <tr style={{ background: 'var(--accent-soft)' }}>
      {columns.map((c) => (
        <td key={c.key}>
          {c.readOnly ? <span className="muted">—</span> : <Field col={c} draft={draft} set={set} />}
        </td>
      ))}
      <td style={{ whiteSpace: 'nowrap' }}>
        <button onClick={onSave} disabled={busy}>{isNew ? 'Adaugă' : 'Salvează'}</button>{' '}
        <button className="ghost" onClick={onCancel} disabled={busy}>Anulează</button>
      </td>
    </tr>
  );
}

function Field({ col, draft, set }) {
  const v = draft[col.key];

  if (col.type === 'checkbox') {
    return <input type="checkbox" checked={!!v} onChange={(e) => set(col.key, e.target.checked)} />;
  }

  if (col.type === 'select') {
    return (
      <select
        value={v === null || v === undefined ? '' : String(v)}
        onChange={(e) => {
          const raw = e.target.value;
          if (raw === '') return set(col.key, null);
          const opt = col.options.find(([value]) => String(value) === raw);
          set(col.key, opt ? opt[0] : raw);
        }}
        style={{ width: '100%' }}
      >
        {col.nullable && <option value="">— niciunul —</option>}
        {col.options.map(([value, text]) => (
          <option key={String(value)} value={String(value)}>{text}</option>
        ))}
      </select>
    );
  }

  if (col.type === 'groups') {
    const selected = Array.isArray(v) ? v : [];
    return (
      <select
        multiple
        size={Math.min(6, Math.max(3, col.options.length))}
        value={selected.map(String)}
        onChange={(e) => set(col.key, Array.from(e.target.selectedOptions, (o) => Number(o.value)))}
        style={{ width: '100%', minHeight: 90 }}
      >
        {col.options.map((g) => (
          <option key={g.id} value={String(g.id)}>{g.name} ({g.studentCount})</option>
        ))}
      </select>
    );
  }

  return (
    <input
      type={col.type === 'number' ? 'number' : 'text'}
      value={v === null || v === undefined ? '' : v}
      onChange={(e) => set(col.key, col.type === 'number' ? e.target.value : e.target.value)}
      style={{ width: '100%' }}
    />
  );
}
