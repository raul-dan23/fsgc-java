import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client.js';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
const DAY_RO = {
  MONDAY: 'Luni', TUESDAY: 'Marți', WEDNESDAY: 'Miercuri', THURSDAY: 'Joi', FRIDAY: 'Vineri',
};
const PROGRAM_RO = { LICENSE: 'Licență', MASTER: 'Master' };
const RESTRICTION_RO = {
  FORBIDDEN: 'Nu poate preda în această sală',
  ONLY_THIS: 'Poate preda doar în această sală',
};
const CATEGORY_RO = {
  DPPD: 'DPPD', CCOC: 'CCOC', DCT: 'DCT', LIMBI_STRAINE: 'Limbi străine',
};
/** [cheie, titlu, ce face, ce se întâmplă dacă e mare / dacă e mic] */
const WEIGHT_FIELDS = [
  ['dailyLoadBalance', 'Echilibrarea încărcării zilnice pe grupă',
    'Compară ziua cea mai plină cu cea mai goală a fiecărei grupe și penalizează diferența.',
    'Mare: orele grupei se împart uniform pe zile. Mic: acceptă o zi cu 5 ore și alta cu una singură.'],
  ['groupGap', 'Evitarea ferestrelor în ziua grupei',
    'Penalizează fiecare modul liber rămas între două ore ale aceleiași grupe, în aceeași zi.',
    'Mare: studenții nu așteaptă între ore. Mic: se acceptă pauze de 2-3 module.'],
  ['lateHoursLicense', 'Evitarea orelor târzii (licență)',
    'Penalizează orele de la modulele 7 și 8 (18:00–21:10) pentru grupele de licență; modulul 8 costă dublu față de 7.',
    'Mare: licența se termină mai devreme. Mic: se umplu și serile. Masteratul nu e afectat — el se ține oricum seara.'],
  ['compactness', 'Compactarea zilei',
    'Penalizează fiecare modul liber dinaintea primei ore a grupei din acea zi.',
    'Mare: ziua începe dimineața. Mic: ziua poate începe la prânz, dacă asta ajută în altă parte.'],
  ['globalWeeklyBalance', 'Echilibrarea pe întreaga săptămână',
    'Se uită la toată facultatea, nu la o grupă: penalizează aglomerarea orelor în aceleași zile.',
    'Mare: sălile și cadrele didactice sunt folosite uniform luni-vineri. Mic: se acceptă o zi foarte plină.'],
  ['professorPreference', 'Preferințele cadrelor didactice',
    'Rezervat pentru intervalele preferate ale cadrelor didactice.',
    'Momentan nu există astfel de preferințe în aplicație, deci valoarea nu schimbă nimic. Indisponibilitățile sunt altceva — ele sunt reguli absolute.'],
  ['professorWeekDays', 'Cel mult 3 zile pe săptămână pentru un cadru didactic',
    'Penalizează fiecare zi peste a treia în care un cadru didactic trebuie să vină la facultate.',
    'Mare: orele i se adună în 2-3 zile, nu câte un modul în fiecare zi. Mic: orele se pot împrăștia pe toată săptămâna. Cine are foarte multe ore va depăși oricum 3 zile.'],
  ['parityPairTogether', 'Ore alternative SI/SP în același interval',
    'Ține cele două jumătăți ale unei ore alternative (o grupă în săptămâna impară, alta în cea pară) în același interval și aceeași sală.',
    'Mare: apar ca o singură celulă în orar, cum arată orarul real. Mic: pot ajunge în zile diferite.'],
  ['roomOversize', 'Sală pe măsura grupei (penalizează locurile goale)',
    'Penalizează fiecare loc gol din sală: o grupă de 27 într-un amfiteatru de 136 costă 109.',
    'Mare: fiecare oră primește cea mai mică sală în care încape, iar amfiteatrele rămân libere pentru cursurile mari. Mic: sala se alege la întâmplare dintre cele care încap.'],
  ['farRoomCommute', 'Timp de mers până la P01',
    'Penalizează o grupă care are un modul în P01 și modulul imediat următor în altă sală (sau invers) — sunt ~20 de minute de mers, iar pauza dintre module e de 10.',
    'Mare: solverul lasă un modul liber între ele sau ține ambele ore în P01. Mic: poate programa drumul imposibil.'],
];

/** "08:00:00" -> 480, pentru sortarea intervalelor unei zile. */
const minutesOf = (t) => {
  if (!t) return -1;
  const [h, m] = String(t).split(':');
  return Number(h) * 60 + Number(m);
};

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
  const years = useMemo(
    () => [...new Set(groups.map((g) => g.year).filter((y) => y != null))].sort((a, b) => a - b),
    [groups],
  );
  /** The eight daily modules, taken from Monday — the day is a column in the slot grid. */
  const modules = useMemo(() => {
    const monday = slots.filter((s) => s.dayOfWeek === 'MONDAY')
      .sort((a, b) => a.slotIndex - b.slotIndex);
    return monday.map((s) => ({
      value: s.slotIndex,
      range: `${hhmm(s.startTime)} – ${hhmm(s.endTime)}`,
      label: `Modulul ${s.slotIndex} · ${hhmm(s.startTime)} – ${hhmm(s.endTime)}`,
    }));
  }, [slots]);

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
          Astea nu sunt reguli, sunt preferințe: solverul le încalcă dacă altfel n-ar avea unde
          pune o oră. Scara e 0–100 și contează doar <i>una față de alta</i> — o pondere de 80 se
          impune în fața uneia de 20 când cele două se bat cap în cap. <b>0 înseamnă „nu mă
          interesează"</b>. Regulile absolute (sală ocupată, grupă în două locuri, zile blocate,
          maximum 5 module pe zi, ziua compactă a unui cadru didactic) nu se negociază și nu apar aici.
        </p>
        {!weights ? <p className="muted">Se încarcă…</p> : (
          <>
            {WEIGHT_FIELDS.map(([key, label, what, effect]) => (
              <div key={key} className="weight-row">
                <div className="weight-label">
                  <div className="t">{label}</div>
                  <div className="d">{what}</div>
                  <div className="d">{effect}</div>
                </div>
                <div className="weight-controls">
                  <input type="range" min="0" max="100" value={weights[key]}
                         onChange={(e) => setWeights({ ...weights, [key]: Number(e.target.value) })} />
                  <input type="number" min="0" value={weights[key]}
                         onChange={(e) => setWeights({ ...weights, [key]: Number(e.target.value) })} />
                </div>
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
        multi="dayOfWeek"
        fields={[
          { name: 'studyProgram', label: 'Program de studiu', type: 'select',
            options: Object.keys(PROGRAM_RO), labels: PROGRAM_RO, required: true },
          { name: 'year', label: 'An', type: 'select', options: years,
            labels: Object.fromEntries(years.map((y) => [y, `Anul ${y}`])), number: true, required: true },
          { name: 'dayOfWeek', label: 'Zilele', type: 'days', options: DAYS, labels: DAY_RO, required: true },
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

      <SpecialBlocksSection groups={groups} slots={slots} modules={modules} />

      <RuleSection
        title="Indisponibilități cadre didactice"
        hint="Intervalul în care profesorul nu poate preda. Lasă orele goale pentru a bloca toată ziua."
        kind="professor-unavailabilities"
        multi="dayOfWeek"
        fields={[
          { name: 'professorId', label: 'Cadrul didactic', type: 'entity', options: professors, required: true },
          { name: 'dayOfWeek', label: 'Zilele', type: 'days', options: DAYS, labels: DAY_RO, required: true },
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
        group={{
          header: 'Cadru didactic',
          by: (r) => (r.professor ? r.professor.id : 0),
          label: (r) => (r.professor ? r.professor.name : '— fără cadru didactic —'),
          chip: (r) => `${DAY_RO[r.dayOfWeek] || r.dayOfWeek} ${r.startTime
            ? `${hhmm(r.startTime)}–${hhmm(r.endTime) || '…'}` : 'toată ziua'}`,
          rank: (r) => DAYS.indexOf(r.dayOfWeek) * 10000 + minutesOf(r.startTime),
        }}
      />

      <RuleSection
        title="Săli interzise / rezervate unui cadru didactic"
        hint="„Nu poate preda” scoate sala din discuție pentru acel cadru didactic. „Poate preda doar” e mai tare: dacă un cadru didactic are măcar o astfel de regulă, orele lui intră numai în sălile trecute aici."
        kind="professor-room-restrictions"
        fields={[
          { name: 'professorId', label: 'Cadrul didactic', type: 'entity', options: professors, required: true },
          { name: 'restrictionType', label: 'Regula', type: 'select',
            options: Object.keys(RESTRICTION_RO), labels: RESTRICTION_RO, required: true },
          { name: 'roomId', label: 'Sala', type: 'entity', options: rooms, required: true },
        ]}
        columns={[
          { label: 'Cadru didactic', render: (r) => (r.professor ? r.professor.name : '—') },
          { label: 'Regula', render: (r) => RESTRICTION_RO[r.restrictionType] || r.restrictionType },
          { label: 'Sala', render: (r) => (r.room ? r.room.name : '—') },
        ]}
        group={{
          header: 'Cadru didactic',
          by: (r) => (r.professor ? r.professor.id : 0),
          label: (r) => (r.professor ? r.professor.name : '—'),
          chip: (r) => `${r.restrictionType === 'ONLY_THIS' ? 'doar' : 'fără'} ${r.room ? r.room.name : '?'}`,
          rank: (r) => (r.restrictionType === 'ONLY_THIS' ? 0 : 1),
        }}
      />

      <RuleSection
        title="Indisponibilități săli"
        hint="Sălile sunt considerate disponibile la toate modulele. Adaugă aici doar excepțiile — intervalele în care sala NU poate fi folosită."
        kind="room-unavailabilities"
        multi="dayOfWeek"
        fields={[
          { name: 'roomId', label: 'Sala', type: 'entity', options: rooms, required: true },
          { name: 'dayOfWeek', label: 'Zilele', type: 'days', options: DAYS, labels: DAY_RO, required: true },
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
        group={{
          header: 'Sala',
          by: (r) => r.roomName || '—',
          label: (r) => r.roomName || '—',
          chip: (r) => `${DAY_RO[r.dayOfWeek] || r.dayOfWeek} ${hhmm(r.startTime)}–${hhmm(r.endTime)}`
            + (r.reason ? ` (${r.reason})` : ''),
          rank: (r) => DAYS.indexOf(r.dayOfWeek) * 10000 + minutesOf(r.startTime),
        }}
      />
    </div>
  );
}

/**
 * Blocajele pe interval, adăugate în bloc: o audiență (un an de studiu întreg, câteva specializări
 * ale lui, sau grupe anume) x toate intervalele bifate în grila zi/modul. Altfel un an cu 4 secții
 * x 4 module ar însemna 16 reguli introduse una câte una.
 */
function SpecialBlocksSection({ groups, slots, modules }) {
  const [items, setItems] = useState(null);
  const [category, setCategory] = useState('DPPD');
  const [audience, setAudience] = useState('YEAR'); // YEAR | GROUPS
  const [program, setProgram] = useState('LICENSE');
  const [year, setYear] = useState('');
  const [specs, setSpecs] = useState([]);       // subset de specializări; gol = toate
  const [groupIds, setGroupIds] = useState([]);
  const [picked, setPicked] = useState([]);     // id-uri de time slot
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [info, setInfo] = useState(null);

  const reload = useCallback(() => {
    api.listRule('special-blocks')
      .then((r) => { setItems(r); setError(null); })
      .catch((e) => { setItems([]); setError(e.message); });
  }, []);
  useEffect(() => { reload(); }, [reload]);

  const yearsOfProgram = useMemo(
    () => [...new Set(groups.filter((g) => g.studyProgram === program).map((g) => g.year))]
      .sort((a, b) => a - b),
    [groups, program],
  );
  /** Specializările programului + anului ales — acestea sunt regulile care se vor crea. */
  const yearSpecs = useMemo(
    () => [...new Set(groups
      .filter((g) => g.studyProgram === program && g.year === Number(year))
      .map((g) => g.specialization).filter(Boolean))].sort(),
    [groups, program, year],
  );
  // dacă se schimbă anul, subsetul de specializări de dinainte nu mai are sens
  useEffect(() => { setSpecs([]); }, [program, year]);

  const slotId = useCallback((day, slotIndex) => {
    const hit = slots.find((x) => x.dayOfWeek === day && x.slotIndex === slotIndex);
    return hit ? hit.id : null;
  }, [slots]);

  const has = (id) => id != null && picked.includes(id);
  const toggle = (id) => {
    if (id == null) return;
    setPicked((p) => (p.includes(id) ? p.filter((x) => x !== id) : [...p, id]));
  };
  /** Un antet bifează sau golește toată linia/coloana, în funcție de ce e deja bifat. */
  const toggleMany = (ids) => {
    const real = ids.filter((x) => x != null);
    const allOn = real.length > 0 && real.every((x) => picked.includes(x));
    setPicked((p) => (allOn ? p.filter((x) => !real.includes(x))
      : [...new Set([...p, ...real])]));
  };

  const chosenSpecs = audience === 'YEAR' ? (specs.length ? specs : yearSpecs) : [];
  const audienceCount = audience === 'YEAR' ? chosenSpecs.length : groupIds.length;
  const total = audienceCount * picked.length;

  async function add() {
    setError(null);
    setInfo(null);
    if (picked.length === 0) {
      setError('Bifează cel puțin un interval în grila de mai jos.');
      return;
    }
    if (audienceCount === 0) {
      setError(audience === 'YEAR' ? 'Alege programul și anul.' : 'Alege cel puțin o grupă.');
      return;
    }
    setBusy(true);
    try {
      const res = await api.addSpecialBlocksBulk({
        category,
        studyProgram: audience === 'YEAR' ? program : null,
        year: audience === 'YEAR' ? Number(year) : null,
        specializations: audience === 'YEAR' ? specs : null,
        studentGroupIds: audience === 'GROUPS' ? groupIds : null,
        timeSlotIds: picked,
      });
      const n = res.created.length;
      setInfo(`S-au adăugat ${n} ${n === 1 ? 'blocaj' : 'blocaje'}`
        + (res.skipped ? ` (${res.skipped} existau deja).` : '.'));
      setPicked([]);
      reload();
    } catch (e) {
      setError('Nu s-a putut adăuga: ' + e.message);
    } finally {
      setBusy(false);
    }
  }

  async function removeMany(ids) {
    setBusy(true);
    setError(null);
    setInfo(null);
    try {
      await api.deleteSpecialBlocks(ids);
      reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  /** Regulile strânse pe (categorie + audiență), ca lista să rămână citibilă la zeci de reguli. */
  const grouped = useMemo(() => {
    const m = new Map();
    (items || []).forEach((r) => {
      const aud = r.studentGroup ? `Grupa ${r.studentGroup.name}`
        : (r.specialization && r.year != null ? `${r.specialization}, anul ${r.year}` : null);
      const key = `${r.category}|${aud}`;
      if (!m.has(key)) m.set(key, { key, category: r.category, audience: aud, rules: [] });
      m.get(key).rules.push(r);
    });
    const dayRank = (d) => DAYS.indexOf(d);
    return [...m.values()]
      .map((g) => ({
        ...g,
        rules: g.rules.sort((a, b) => dayRank(a.dayOfWeek) - dayRank(b.dayOfWeek)
          || (a.timeSlot?.slotIndex || 0) - (b.timeSlot?.slotIndex || 0)),
      }))
      .sort((a, b) => a.category.localeCompare(b.category)
        || String(a.audience).localeCompare(String(b.audience)));
  }, [items]);

  return (
    <div className="panel">
      <h2>Blocaje pe interval (DPPD / CCOC / DCT / Limbi străine)</h2>
      <p className="muted">
        Intervalele bifate sunt rezervate pentru categoria aleasă: acolo nu se programează nimic
        pentru audiența selectată, iar în orar celulele apar colorate cu roșu. Poți bifa mai multe
        module deodată și se creează câte o regulă pentru fiecare specializare a anului.
      </p>

      <div className="row" style={{ marginBottom: 12 }}>
        <div className="field">
          <label>Categoria</label>
          <select value={category} onChange={(e) => setCategory(e.target.value)} style={{ minWidth: 150 }}>
            {Object.keys(CATEGORY_RO).map((c) => <option key={c} value={c}>{CATEGORY_RO[c]}</option>)}
          </select>
        </div>
        <div className="field">
          <label>Se aplică la</label>
          <select value={audience} onChange={(e) => setAudience(e.target.value)} style={{ minWidth: 190 }}>
            <option value="YEAR">Un an de studiu întreg</option>
            <option value="GROUPS">Grupe alese</option>
          </select>
        </div>
        {audience === 'YEAR' ? (
          <>
            <div className="field">
              <label>Program de studiu</label>
              <select value={program} onChange={(e) => setProgram(e.target.value)} style={{ minWidth: 130 }}>
                {Object.keys(PROGRAM_RO).map((k) => <option key={k} value={k}>{PROGRAM_RO[k]}</option>)}
              </select>
            </div>
            <div className="field">
              <label>An</label>
              <select value={year} onChange={(e) => setYear(e.target.value)} style={{ minWidth: 110 }}>
                <option value="">— alege —</option>
                {yearsOfProgram.map((y) => <option key={y} value={y}>{`Anul ${y}`}</option>)}
              </select>
            </div>
          </>
        ) : null}
      </div>

      {audience === 'YEAR' && yearSpecs.length > 0 && (
        <div style={{ marginBottom: 12 }}>
          <div className="picker-label">
            Specializări ({specs.length === 0 ? 'toate cele ' : `${specs.length} din `}{yearSpecs.length})
            {specs.length > 0 && (
              <button className="ghost" onClick={() => setSpecs([])}>toate</button>
            )}
          </div>
          <div className="chips">
            {yearSpecs.map((sp) => {
              const on = specs.length === 0 || specs.includes(sp);
              return (
                <button key={sp} type="button" className={`chip${on ? ' on' : ''}`}
                        onClick={() => setSpecs((cur) => {
                          const base = cur.length === 0 ? yearSpecs : cur;
                          return base.includes(sp) ? base.filter((x) => x !== sp) : [...base, sp];
                        })}>
                  {sp}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {audience === 'GROUPS' && (
        <div style={{ marginBottom: 12 }}>
          <div className="picker-label">Grupe ({groupIds.length} alese)</div>
          <div className="chips">
            {groups.map((g) => (
              <button key={g.id} type="button"
                      className={`chip${groupIds.includes(g.id) ? ' on' : ''}`}
                      onClick={() => setGroupIds((cur) => (cur.includes(g.id)
                        ? cur.filter((x) => x !== g.id) : [...cur, g.id]))}>
                {g.name}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="picker-label">
        Intervale ({picked.length} bifate) — click pe zi sau pe modul bifează tot rândul/coloana
        {picked.length > 0 && <button className="ghost" onClick={() => setPicked([])}>golește</button>}
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table className="slot-picker">
          <thead>
            <tr>
              <th />
              {DAYS.map((d) => (
                <th key={d}>
                  <button type="button" className="ghost"
                          onClick={() => toggleMany(modules.map((m) => slotId(d, m.value)))}>
                    {DAY_RO[d]}
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {modules.map((m) => (
              <tr key={m.value}>
                <th>
                  <button type="button" className="ghost"
                          onClick={() => toggleMany(DAYS.map((d) => slotId(d, m.value)))}>
                    {`M${m.value} · ${m.range}`}
                  </button>
                </th>
                {DAYS.map((d) => {
                  const id = slotId(d, m.value);
                  return (
                    <td key={d}>
                      <button type="button" disabled={id == null}
                              className={`slot-cell${has(id) ? ' on' : ''}`}
                              onClick={() => toggle(id)}>
                        {has(id) ? '✓' : ''}
                      </button>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="row" style={{ marginTop: 12 }}>
        <button onClick={add} disabled={busy || total === 0}>
          {total > 0 ? `Adaugă ${total} ${total === 1 ? 'blocaj' : 'blocaje'}` : 'Adaugă blocaje'}
        </button>
        {total > 0 && (
          <span className="muted">
            {audienceCount} {audienceCount === 1 ? 'audiență' : 'audiențe'} × {picked.length}{' '}
            {picked.length === 1 ? 'interval' : 'intervale'}
          </span>
        )}
      </div>

      {error && <p className="badge bad" style={{ display: 'inline-block', marginTop: 10 }}>{error}</p>}
      {info && <p className="badge ok" style={{ display: 'inline-block', marginTop: 10 }}>{info}</p>}

      {items === null ? (
        <p className="muted" style={{ marginTop: 12 }}>Se încarcă…</p>
      ) : grouped.length === 0 ? (
        <p className="muted" style={{ marginTop: 12, marginBottom: 0 }}>Niciun blocaj definit.</p>
      ) : (
        <div style={{ overflowX: 'auto', marginTop: 14 }}>
          <table>
            <thead>
              <tr>
                <th style={{ width: 120 }}>Categoria</th>
                <th style={{ width: 200 }}>Se aplică la</th>
                <th>Intervale blocate</th>
                <th style={{ width: 110 }}>Acțiuni</th>
              </tr>
            </thead>
            <tbody>
              {grouped.map((g) => (
                <tr key={g.key}>
                  <td>{CATEGORY_RO[g.category] || g.category}</td>
                  <td>
                    {g.audience || (
                      <span className="badge warn">Audiență incompletă — regula nu se aplică</span>
                    )}
                  </td>
                  <td>
                    <div className="chips">
                      {g.rules.map((r) => (
                        <button key={r.id} type="button" className="chip on" disabled={busy}
                                title="Șterge acest interval"
                                onClick={() => removeMany([r.id])}>
                          {`${DAY_RO[r.dayOfWeek] || r.dayOfWeek} M${r.timeSlot ? r.timeSlot.slotIndex : '?'}`}
                          <span className="x">×</span>
                        </button>
                      ))}
                    </div>
                  </td>
                  <td>
                    <button className="danger" disabled={busy}
                            onClick={() => removeMany(g.rules.map((r) => r.id))}>
                      Șterge tot
                    </button>
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

/**
 * One rule type: a form row that adds, and a table that lists what is stored. Fields declare their
 * own control; `transform` reshapes the form into the API body when the two differ (e.g. day +
 * module become a single timeSlotId).
 */
/**
 * @param multi numele câmpului care poate ține mai multe valori (zilele): se trimite câte o
 *              regulă pentru fiecare valoare bifată, altfel „marți și joi" ar cere două treceri
 *              prin formular pentru aceeași regulă.
 * @param group strânge regulile care privesc același lucru (un cadru didactic, o sală) într-un
 *              singur rând, cu câte un chip per interval: 30 de profesori × 5 zile ar face 150
 *              de rânduri prin care nu mai găsești nimic. {header, by, label, chip, rank}
 */
function RuleSection({ title, hint, kind, fields, columns, transform, multi, group }) {
  const [items, setItems] = useState(null);
  const [form, setForm] = useState({});
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);

  const reload = useCallback(() => {
    api.listRule(kind)
      .then((r) => { setItems(r); setError(null); })
      .catch((e) => { setItems([]); setError(e.message); });
  }, [kind]);

  useEffect(() => { reload(); }, [reload]);

  const visible = fields.filter((f) => !f.showIf || f.showIf(form));

  async function add() {
    const empty = (v) => v === undefined || v === '' || v === null
      || (Array.isArray(v) && v.length === 0);
    const missing = visible.filter((f) => f.required && empty(form[f.name])).map((f) => f.label);
    if (missing.length) {
      setError('Completează: ' + missing.join(', '));
      return;
    }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const body = transform ? transform(form) : form;
      const values = multi ? [].concat(form[multi]) : [null];
      for (const v of values) {
        await api.addRule(kind, multi ? { ...body, [multi]: v } : body);
      }
      setForm({});
      if (values.length > 1) {
        setNotice(`S-au adăugat ${values.length} reguli, câte una pentru fiecare zi bifată.`);
        setTimeout(() => setNotice(null), 5000);
      }
      reload();
    } catch (e) {
      setError('Nu s-a putut adăuga: ' + e.message);
    } finally {
      setBusy(false);
    }
  }

  async function del(...ids) {
    setBusy(true);
    try {
      for (const id of ids) {
        await api.deleteRule(kind, id);
      }
      reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  /** Regulile strânse pe subiectul lor, în ordinea în care se citesc. */
  const grouped = useMemo(() => {
    if (!group || !items) return [];
    const map = new Map();
    items.forEach((r) => {
      const key = String(group.by(r));
      if (!map.has(key)) map.set(key, { key, label: group.label(r), rules: [] });
      map.get(key).rules.push(r);
    });
    return [...map.values()]
      .map((g) => ({ ...g, rules: g.rules.slice().sort((a, b) => group.rank(a) - group.rank(b)) }))
      .sort((a, b) => String(a.label).localeCompare(String(b.label)));
  }, [items, group]);

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
      {notice && <p className="badge ok" style={{ display: 'inline-block', marginTop: 10 }}>{notice}</p>}

      {items === null ? (
        <p className="muted" style={{ marginTop: 12 }}>Se încarcă…</p>
      ) : items.length === 0 ? (
        <p className="muted" style={{ marginTop: 12, marginBottom: 0 }}>Nicio regulă definită.</p>
      ) : (
        <div style={{ overflowX: 'auto', marginTop: 14 }}>
          <table>
            <thead>
              <tr>
                {group ? (
                  <>
                    <th style={{ width: 240 }}>{group.header}</th>
                    <th>Intervale</th>
                  </>
                ) : columns.map((c) => <th key={c.label}>{c.label}</th>)}
                <th style={{ width: 110 }}>Acțiuni</th>
              </tr>
            </thead>
            <tbody>
              {group ? grouped.map((g) => (
                <tr key={g.key}>
                  <td>{g.label}</td>
                  <td>
                    <div className="chips">
                      {g.rules.map((r) => (
                        <button key={r.id} type="button" className="chip on" disabled={busy}
                                title="Click pentru a șterge acest interval"
                                onClick={() => del(r.id)}>
                          {group.chip(r)}<span className="x">×</span>
                        </button>
                      ))}
                    </div>
                  </td>
                  <td>
                    <button className="danger" disabled={busy}
                            onClick={() => del(...g.rules.map((r) => r.id))}>
                      Șterge tot
                    </button>
                  </td>
                </tr>
              )) : items.map((it) => (
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

  // Mai multe zile deodată: aceeași regulă e rareori pentru o singură zi.
  if (field.type === 'days') {
    const picked = Array.isArray(form[field.name]) ? form[field.name] : [];
    const toggle = (d) => set(picked.includes(d) ? picked.filter((x) => x !== d) : [...picked, d]);
    const all = picked.length === field.options.length;
    return (
      <div className="chips" style={{ paddingTop: 4 }}>
        {field.options.map((d) => (
          <button key={d} type="button" className={`chip${picked.includes(d) ? ' on' : ''}`}
                  onClick={() => toggle(d)}>
            {field.labels ? field.labels[d] : d}
          </button>
        ))}
        <button type="button" className="chip" style={{ fontStyle: 'italic' }}
                onClick={() => set(all ? [] : [...field.options])}>
          {all ? 'niciuna' : 'toate'}
        </button>
      </div>
    );
  }

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
