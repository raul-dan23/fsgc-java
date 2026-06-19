import { useEffect, useState } from 'react';
import { api } from '../api/client.js';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
const DAY_RO = { MONDAY: 'Luni', TUESDAY: 'Marți', WEDNESDAY: 'Miercuri', THURSDAY: 'Joi', FRIDAY: 'Vineri' };
const WEIGHT_FIELDS = [
  ['dailyLoadBalance', 'Echilibrare zilnică / grupă (bug Luni-Vineri)'],
  ['groupGap', 'Evitarea găurilor în ziua grupei'],
  ['lateHoursLicense', 'Evitarea orelor târzii (licență)'],
  ['compactness', 'Compactarea zilei'],
  ['globalWeeklyBalance', 'Echilibrare globală pe săptămână'],
  ['professorPreference', 'Preferințe profesor (opțional)'],
];

export default function ConstraintsPage() {
  const [weights, setWeights] = useState(null);
  const [saved, setSaved] = useState(false);
  const [groups, setGroups] = useState([]);
  const [professors, setProfessors] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [slots, setSlots] = useState([]);

  useEffect(() => {
    api.getWeights().then(setWeights);
    api.groups().then(setGroups);
    api.professors().then(setProfessors);
    api.rooms().then(setRooms);
    api.timeslots().then(setSlots);
  }, []);

  async function saveWeights() {
    await api.saveWeights(weights);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  }

  return (
    <div>
      <h1>Constrângeri & ponderi</h1>

      <div className="panel">
        <h2>Ponderi soft (calitatea orarului)</h2>
        {!weights ? <p className="muted">Se încarcă…</p> : (
          <>
            {WEIGHT_FIELDS.map(([key, label]) => (
              <div key={key} className="row" style={{ marginBottom: 10, alignItems: 'center' }}>
                <div style={{ width: 360 }}>{label}</div>
                <input type="range" min="0" max="100" value={weights[key]}
                       onChange={(e) => setWeights({ ...weights, [key]: Number(e.target.value) })} />
                <input type="number" min="0" value={weights[key]} style={{ width: 80 }}
                       onChange={(e) => setWeights({ ...weights, [key]: Number(e.target.value) })} />
              </div>
            ))}
            <button onClick={saveWeights}>Salvează ponderile</button>
            {saved && <span className="badge ok" style={{ marginLeft: 10 }}>Salvat</span>}
            <p className="muted">Ponderile sunt aplicate la următoarea generare.</p>
          </>
        )}
      </div>

      <RuleSection
        title="Zile blocate (ani terminali)"
        kind="blocked-days"
        columns={['studyProgram', 'year', 'dayOfWeek', 'semester', 'academicYear']}
        render={(r) => `${r.studyProgram} an ${r.year} — ${DAY_RO[r.dayOfWeek]} (${r.semester || ''} ${r.academicYear || ''})`}
        fields={[
          { name: 'studyProgram', type: 'select', options: ['LICENSE', 'MASTER'] },
          { name: 'year', type: 'number' },
          { name: 'dayOfWeek', type: 'select', options: DAYS, labels: DAY_RO },
          { name: 'semester', type: 'text' },
          { name: 'academicYear', type: 'text' },
        ]}
      />

      <RuleSection
        title="Blocaje interval (DPPD / CCOC / DCT / Limbi străine)"
        kind="special-blocks"
        render={(r) => `${r.category} — ${r.specialization || (r.studentGroup && r.studentGroup.name) || '?'} an ${r.year || ''} — ${DAY_RO[r.dayOfWeek]}`}
        fields={[
          { name: 'category', type: 'select', options: ['DPPD', 'CCOC', 'DCT', 'LIMBI_STRAINE'] },
          { name: 'specialization', type: 'text' },
          { name: 'year', type: 'number' },
          { name: 'dayOfWeek', type: 'select', options: DAYS, labels: DAY_RO },
          { name: 'timeSlotId', type: 'slot', slots },
        ]}
      />

      <RuleSection
        title="Indisponibilități profesori"
        kind="professor-unavailabilities"
        render={(r) => `prof #${r.professor ? r.professor.id : '?'} — ${DAY_RO[r.dayOfWeek]} ${r.startTime || 'toată ziua'}${r.endTime ? '–' + r.endTime : ''}`}
        fields={[
          { name: 'professorId', type: 'entity', options: professors },
          { name: 'dayOfWeek', type: 'select', options: DAYS, labels: DAY_RO },
          { name: 'startTime', type: 'time' },
          { name: 'endTime', type: 'time' },
        ]}
      />

      <RuleSection
        title="Restricții sală — profesor"
        kind="professor-room-restrictions"
        render={(r) => `prof #${r.professor ? r.professor.id : '?'} ${r.restrictionType} sala #${r.room ? r.room.id : '?'}`}
        fields={[
          { name: 'professorId', type: 'entity', options: professors },
          { name: 'roomId', type: 'entity', options: rooms },
          { name: 'restrictionType', type: 'select', options: ['ONLY_THIS', 'FORBIDDEN'] },
        ]}
      />

      <RuleSection
        title="Disponibilitate săli (pe zi/interval)"
        kind="room-availabilities"
        render={(r) => `sala #${r.room ? r.room.id : '?'} — ${DAY_RO[r.dayOfWeek]} ${r.startTime}–${r.endTime}`}
        fields={[
          { name: 'roomId', type: 'entity', options: rooms },
          { name: 'dayOfWeek', type: 'select', options: DAYS, labels: DAY_RO },
          { name: 'startTime', type: 'time' },
          { name: 'endTime', type: 'time' },
        ]}
      />
    </div>
  );
}

function RuleSection({ title, kind, fields, render }) {
  const [items, setItems] = useState([]);
  const [form, setForm] = useState({});
  const [error, setError] = useState(null);

  function reload() { api.listRule(kind).then(setItems).catch((e) => setError(e.message)); }
  useEffect(reload, [kind]);

  async function add() {
    // basic required-field validation (enum/entity/slot selects must be chosen)
    const missing = fields
      .filter((f) => f.type === 'select' || f.type === 'entity' || f.type === 'slot')
      .filter((f) => form[f.name] === undefined || form[f.name] === '' || form[f.name] === null)
      .map((f) => f.name);
    if (missing.length) {
      setError('Completează câmpurile: ' + missing.join(', '));
      return;
    }
    setError(null);
    try {
      await api.addRule(kind, form);
      setForm({});
      reload();
    } catch (e) {
      setError('Nu s-a putut adăuga: ' + e.message);
    }
  }
  async function del(id) {
    try {
      await api.deleteRule(kind, id);
      reload();
    } catch (e) {
      setError(e.message);
    }
  }

  return (
    <div className="panel">
      <h2>{title}</h2>
      <div className="row">
        {fields.map((f) => (
          <div className="field" key={f.name}>
            <label>{f.name}</label>
            {f.type === 'select' && (
              <select value={form[f.name] || ''} onChange={(e) => setForm({ ...form, [f.name]: e.target.value })}>
                <option value="">—</option>
                {f.options.map((o) => <option key={o} value={o}>{f.labels ? f.labels[o] : o}</option>)}
              </select>
            )}
            {f.type === 'entity' && (
              <select value={form[f.name] || ''} onChange={(e) => setForm({ ...form, [f.name]: Number(e.target.value) })}>
                <option value="">—</option>
                {f.options.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
              </select>
            )}
            {f.type === 'slot' && (
              <select value={form[f.name] || ''} onChange={(e) => setForm({ ...form, [f.name]: Number(e.target.value) })}>
                <option value="">—</option>
                {f.slots.map((s) => (
                  <option key={s.id} value={s.id}>{DAY_RO[s.dayOfWeek]} M{s.slotIndex} ({s.startTime})</option>
                ))}
              </select>
            )}
            {(f.type === 'text' || f.type === 'number' || f.type === 'time') && (
              <input
                type={f.type === 'number' ? 'number' : f.type === 'time' ? 'time' : 'text'}
                value={form[f.name] || ''}
                style={{ width: 130 }}
                onChange={(e) => setForm({
                  ...form,
                  [f.name]: f.type === 'number' ? Number(e.target.value) : e.target.value,
                })}
              />
            )}
          </div>
        ))}
        <button onClick={add}>Adaugă</button>
      </div>
      {error && <p className="badge bad" style={{ marginTop: 10 }}>{error}</p>}
      {items.length > 0 && (
        <table style={{ marginTop: 12 }}>
          <tbody>
            {items.map((it) => (
              <tr key={it.id}>
                <td>{render(it)}</td>
                <td style={{ width: 90 }}>
                  <button className="danger" onClick={() => del(it.id)}>Șterge</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
