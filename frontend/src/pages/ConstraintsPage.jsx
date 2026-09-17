import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client.js';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
const DAY_RO = {
  MONDAY: 'Luni', TUESDAY: 'Marți', WEDNESDAY: 'Miercuri', THURSDAY: 'Joi', FRIDAY: 'Vineri',
};
const PROGRAM_RO = { LICENSE: 'Licență', MASTER: 'Master' };
const CATEGORY_RO = {
  DPPD: 'DPPD', CCOC: 'CCOC', DCT: 'DCT', LIMBI_STRAINE: 'Limbi străine',
};
const RESTRICTION_RO = {
  ONLY_THIS: 'Doar în această sală',
  FORBIDDEN: 'Interzis în această sală',
};

const WEIGHT_FIELDS = [
  ['dailyLoadBalance', 'Echilibrarea încărcării zilnice pe grupă'],
  ['groupGap', 'Evitarea ferestrelor în ziua grupei'],
  ['lateHoursLicense', 'Evitarea orelor târzii (licență)'],
  ['compactness', 'Compactarea zilei'],
  ['globalWeeklyBalance', 'Echilibrarea pe întreaga săptămână'],
  ['professorPreference', 'Preferințele cadrelor didactice'],
  ['parityPairTogether', 'Ore alternative SI/SP în același interval'],
  ['roomOversize', 'Sală pe măsura grupei (penalizează locurile goale)'],
];

/** "08:00:00" -> "08:00" */
const hhmm = (t) => (t ? String(t).slice(0, 5) : '');

export default function ConstraintsPage() {
  const [weights, setWeights] = useState(null);
  const [saved, setSaved] = useState(false);
  const [groups, setGroups] = useState([]);
  const [professors, setProfessors] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [slots, setSlots] = useState([]);

  useEffect(() => {
    api.getWeights().then(setWeights).catch(() => {});
    api.groups().then(setGroups).catch(() => {});
    api.professors().then(setProfessors).catch(() => {});
    api.rooms().then(setRooms).catch(() => {});
    api.timeslots().then(setSlots).catch(() => {});
  }, []);

  async function saveWeights() {
    await api.saveWeights(weights);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  }

  // Dropdown sources derived from the imported data, so nothing is free text any more.
  const specializations = useMemo(
    () => [...new Set(groups.map((g) => g.specialization).filter(Boolean))].sort(),
    [groups],
  );
  const years = useMemo(
    () => [...new Set(groups.map((g) => g.year).filter((y) => y != null))].sort((a, b) => a - b),
    [groups],
  );
  /** The eight daily modules, taken from Monday — the day is chosen in its own field. */
  const modules = useMemo(() => {
    const monday = slots.filter((s) => s.dayOfWeek === 'MONDAY')
      .sort((a, b) => a.slotIndex - b.slotIndex);
    return monday.map((s) => ({
      value: s.slotIndex,
      label: `Modulul ${s.slotIndex} · ${hhmm(s.startTime)} – ${hhmm(s.endTime)}`,
    }));
  }, [slots]);

  /** Resolves the {day, module} pair the form collects into the single id the API expects. */
  const resolveSlotId = useCallback((day, slotIndex) => {
    const hit = slots.find((s) => s.dayOfWeek === day && s.slotIndex === Number(slotIndex));
    return hit ? hit.id : null;
  }, [slots]);

  const groupName = (id) => {
    const g = groups.find((x) => x.id === id);
    return g ? g.name : null;
  };

  return (
    <div>
      <h1>Constrângeri</h1>
      <p className="muted" style={{ marginTop: -10, marginBottom: 22 }}>
        Regulile de mai jos sunt luate în calcul la următoarea generare. Cele „hard” nu pot fi
        încălcate; ponderile de mai jos doar modelează calitatea orarului.
      </p>

      <div className="panel">
        <h2>Ponderi pentru calitatea orarului</h2>
        <p className="muted">
          Valori mai mari înseamnă că solverul se străduiește mai mult pentru criteriul respectiv.
        </p>
        {!weights ? <p className="muted">Se încarcă…</p> : (
          <>
            {WEIGHT_FIELDS.map(([key, label]) => (
              <div key={key} className="row" style={{ marginBottom: 10, alignItems: 'center' }}>
                <div style={{ width: 340, fontSize: 14 }}>{label}</div>
                <input type="range" min="0" max="100" value={weights[key]} style={{ width: 240 }}
                       onChange={(e) => setWeights({ ...weights, [key]: Number(e.target.value) })} />
                <input type="number" min="0" value={weights[key]} style={{ width: 80 }}
                       onChange={(e) => setWeights({ ...weights, [key]: Number(e.target.value) })} />
              </div>
            ))}
            <button onClick={saveWeights}>Salvează ponderile</button>
            {saved && <span className="badge ok" style={{ marginLeft: 10 }}>Salvat</span>}
          </>
        )}
      </div>

      <RuleSection
        title="Zile blocate pentru ani terminali"
        hint="Într-o zi blocată nu se programează nimic pentru programul și anul selectat."
        kind="blocked-days"
        fields={[
          { name: 'studyProgram', label: 'Program de studiu', type: 'select',
            options: Object.keys(PROGRAM_RO), labels: PROGRAM_RO, required: true },
          { name: 'year', label: 'An', type: 'select', options: years,
            labels: Object.fromEntries(years.map((y) => [y, `Anul ${y}`])), number: true, required: true },
          { name: 'dayOfWeek', label: 'Ziua', type: 'select', options: DAYS, labels: DAY_RO, required: true },
          { name: 'semester', label: 'Semestrul', type: 'select', options: ['1', '2'],
            labels: { 1: 'Semestrul I', 2: 'Semestrul al II-lea' } },
          { name: 'academicYear', label: 'An universitar', type: 'text', placeholder: '2025-2026' },
        ]}
        columns={[
          { label: 'Program', render: (r) => PROGRAM_RO[r.studyProgram] || r.studyProgram },
          { label: 'An', render: (r) => (r.year ? `Anul ${r.year}` : '—') },
          { label: 'Ziua blocată', render: (r) => DAY_RO[r.dayOfWeek] || r.dayOfWeek },
          { label: 'Semestrul', render: (r) => (r.semester ? `Semestrul ${r.semester}` : '—') },
          { label: 'An universitar', render: (r) => r.academicYear || '—' },
        ]}
      />

      <RuleSection
        title="Blocaje pe interval (DPPD / CCOC / DCT / Limbi străine)"
        hint="Rezervă un interval pentru o categorie specială. Alege fie o grupă anume, fie o specializare împreună cu anul — altfel regula nu se aplică la nimic."
        kind="special-blocks"
        fields={[
          { name: 'category', label: 'Categoria', type: 'select',
            options: Object.keys(CATEGORY_RO), labels: CATEGORY_RO, required: true },
          { name: 'audience', label: 'Se aplică la', type: 'select',
            options: ['GROUP', 'SPEC'], labels: { GROUP: 'O grupă anume', SPEC: 'Specializare + an' },
            required: true },
          { name: 'studentGroupId', label: 'Grupa', type: 'entity', options: groups,
            showIf: (f) => f.audience === 'GROUP', required: true },
          { name: 'specialization', label: 'Specializarea', type: 'select', options: specializations,
            showIf: (f) => f.audience === 'SPEC', required: true },
          { name: 'year', label: 'An', type: 'select', options: years,
            labels: Object.fromEntries(years.map((y) => [y, `Anul ${y}`])), number: true,
            showIf: (f) => f.audience === 'SPEC', required: true },
          { name: 'dayOfWeek', label: 'Ziua', type: 'select', options: DAYS, labels: DAY_RO, required: true },
          { name: 'slotIndex', label: 'Intervalul orar', type: 'select', options: modules.map((m) => m.value),
            labels: Object.fromEntries(modules.map((m) => [m.value, m.label])), number: true, required: true },
        ]}
        transform={(f) => {
          const out = {
            category: f.category,
            dayOfWeek: f.dayOfWeek,
            timeSlotId: resolveSlotId(f.dayOfWeek, f.slotIndex),
          };
          if (f.audience === 'GROUP') {
            out.studentGroupId = f.studentGroupId;
          } else {
            out.specialization = f.specialization;
            out.year = f.year;
          }
          return out;
        }}
        columns={[
          { label: 'Categoria', render: (r) => CATEGORY_RO[r.category] || r.category },
          {
            label: 'Se aplică la',
            render: (r) => {
              if (r.studentGroup) return `Grupa ${r.studentGroup.name}`;
              if (r.specialization && r.year) return `${r.specialization}, anul ${r.year}`;
              return <span className="badge warn">Audiență incompletă — regula nu se aplică</span>;
            },
          },
          { label: 'Ziua', render: (r) => DAY_RO[r.dayOfWeek] || r.dayOfWeek },
          {
            label: 'Intervalul',
            render: (r) => (r.timeSlot
              ? `Modulul ${r.timeSlot.slotIndex} · ${hhmm(r.timeSlot.startTime)} – ${hhmm(r.timeSlot.endTime)}`
              : '—'),
          },
        ]}
      />

      <RuleSection
        title="Indisponibilități cadre didactice"
        hint="Intervalul în care profesorul nu poate preda. Lasă orele goale pentru a bloca toată ziua."
        kind="professor-unavailabilities"
        fields={[
          { name: 'professorId', label: 'Cadrul didactic', type: 'entity', options: professors, required: true },
          { name: 'dayOfWeek', label: 'Ziua', type: 'select', options: DAYS, labels: DAY_RO, required: true },
          { name: 'startTime', label: 'De la ora', type: 'time' },
          { name: 'endTime', label: 'Până la ora', type: 'time' },
        ]}
        columns={[
          { label: 'Cadru didactic', render: (r) => (r.professor ? r.professor.name : '—') },
          { label: 'Ziua', render: (r) => DAY_RO[r.dayOfWeek] || r.dayOfWeek },
          {
            label: 'Interval',
            render: (r) => (r.startTime
              ? `${hhmm(r.startTime)} – ${hhmm(r.endTime) || '…'}`
              : 'Toată ziua'),
          },
        ]}
      />

      <RuleSection
        title="Restricții de sală pentru cadre didactice"
        hint="Obligă un cadru didactic într-o anumită sală sau, dimpotrivă, îi interzice o sală."
        kind="professor-room-restrictions"
        fields={[
          { name: 'professorId', label: 'Cadrul didactic', type: 'entity', options: professors, required: true },
          { name: 'restrictionType', label: 'Tipul restricției', type: 'select',
            options: Object.keys(RESTRICTION_RO), labels: RESTRICTION_RO, required: true },
          { name: 'roomId', label: 'Sala', type: 'entity', options: rooms, required: true },
        ]}
        columns={[
          { label: 'Cadru didactic', render: (r) => (r.professor ? r.professor.name : '—') },
          { label: 'Restricție', render: (r) => RESTRICTION_RO[r.restrictionType] || r.restrictionType },
          { label: 'Sala', render: (r) => (r.room ? r.room.name : '—') },
        ]}
      />

      <RuleSection
        title="Indisponibilități săli"
        hint="Sălile sunt considerate disponibile la toate modulele. Adaugă aici doar excepțiile — intervalele în care sala NU poate fi folosită."
        kind="room-unavailabilities"
        fields={[
          { name: 'roomId', label: 'Sala', type: 'entity', options: rooms, required: true },
          { name: 'dayOfWeek', label: 'Ziua', type: 'select', options: DAYS, labels: DAY_RO, required: true },
          { name: 'startTime', label: 'De la ora', type: 'time', required: true },
          { name: 'endTime', label: 'Până la ora', type: 'time', required: true },
          { name: 'reason', label: 'Motiv (opțional)', type: 'text', placeholder: 'ex. renovare' },
        ]}
        columns={[
          { label: 'Sala', render: (r) => r.roomName || '—' },
          { label: 'Ziua', render: (r) => DAY_RO[r.dayOfWeek] || r.dayOfWeek },
          { label: 'Interval blocat', render: (r) => `${hhmm(r.startTime)} – ${hhmm(r.endTime)}` },
          { label: 'Motiv', render: (r) => r.reason || '—' },
        ]}
      />
    </div>
  );
}

/**
 * One rule type: a form row that adds, and a table that lists what is stored. Fields declare their
 * own control; `transform` reshapes the form into the API body when the two differ (e.g. day +
 * module become a single timeSlotId).
 */
function RuleSection({ title, hint, kind, fields, columns, transform }) {
  const [items, setItems] = useState(null);
  const [form, setForm] = useState({});
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const reload = useCallback(() => {
    api.listRule(kind)
      .then((r) => { setItems(r); setError(null); })
      .catch((e) => { setItems([]); setError(e.message); });
  }, [kind]);

  useEffect(() => { reload(); }, [reload]);

  const visible = fields.filter((f) => !f.showIf || f.showIf(form));

  async function add() {
    const missing = visible
      .filter((f) => f.required)
      .filter((f) => form[f.name] === undefined || form[f.name] === '' || form[f.name] === null)
      .map((f) => f.label);
    if (missing.length) {
      setError('Completează: ' + missing.join(', '));
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.addRule(kind, transform ? transform(form) : form);
      setForm({});
      reload();
    } catch (e) {
      setError('Nu s-a putut adăuga: ' + e.message);
    } finally {
      setBusy(false);
    }
  }

  async function del(id) {
    setBusy(true);
    try {
      await api.deleteRule(kind, id);
      reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <h2>{title}</h2>
      {hint && <p className="muted">{hint}</p>}

      <div className="row" style={{ marginBottom: 4 }}>
        {visible.map((f) => (
          <div className="field" key={f.name}>
            <label>{f.label}</label>
            <Control field={f} form={form} setForm={setForm} />
          </div>
        ))}
        <button onClick={add} disabled={busy}>Adaugă</button>
      </div>

      {error && <p className="badge bad" style={{ display: 'inline-block', marginTop: 10 }}>{error}</p>}

      {items === null ? (
        <p className="muted" style={{ marginTop: 12 }}>Se încarcă…</p>
      ) : items.length === 0 ? (
        <p className="muted" style={{ marginTop: 12, marginBottom: 0 }}>Nicio regulă definită.</p>
      ) : (
        <div style={{ overflowX: 'auto', marginTop: 14 }}>
          <table>
            <thead>
              <tr>
                {columns.map((c) => <th key={c.label}>{c.label}</th>)}
                <th style={{ width: 90 }}>Acțiuni</th>
              </tr>
            </thead>
            <tbody>
              {items.map((it) => (
                <tr key={it.id}>
                  {columns.map((c) => <td key={c.label}>{c.render(it)}</td>)}
                  <td>
                    <button className="danger" onClick={() => del(it.id)} disabled={busy}>Șterge</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function Control({ field, form, setForm }) {
  const set = (v) => setForm({ ...form, [field.name]: v });
  const value = form[field.name] === undefined || form[field.name] === null ? '' : form[field.name];

  if (field.type === 'select') {
    return (
      <select value={value} style={{ minWidth: 150 }}
              onChange={(e) => set(e.target.value === '' ? ''
                : field.number ? Number(e.target.value) : e.target.value)}>
        <option value="">— alege —</option>
        {field.options.map((o) => (
          <option key={o} value={o}>{field.labels ? field.labels[o] : o}</option>
        ))}
      </select>
    );
  }

  if (field.type === 'entity') {
    return (
      <select value={value} style={{ minWidth: 190 }}
              onChange={(e) => set(e.target.value === '' ? '' : Number(e.target.value))}>
        <option value="">— alege —</option>
        {field.options.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
      </select>
    );
  }

  return (
    <input
      type={field.type === 'time' ? 'time' : field.type === 'number' ? 'number' : 'text'}
      value={value}
      placeholder={field.placeholder || ''}
      style={{ width: field.type === 'time' ? 120 : 150 }}
      onChange={(e) => set(field.type === 'number' ? Number(e.target.value) : e.target.value)}
    />
  );
}
