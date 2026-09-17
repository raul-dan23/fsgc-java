import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';

const RESULT_KEY = 'orar.lastResult';
const DAY_RO = {
  MONDAY: 'Luni', TUESDAY: 'Marți', WEDNESDAY: 'Miercuri', THURSDAY: 'Joi', FRIDAY: 'Vineri',
};
const TYPE_RO = { COURSE: 'Curs', SEMINAR: 'Seminar', LAB: 'Laborator' };

/** 90 -> "1 min 30 s", 300 -> "5 min" */
function human(sec) {
  if (sec < 60) return `${sec} s`;
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return s ? `${m} min ${s} s` : `${m} min`;
}

export default function GeneratePage() {
  const [seconds, setSeconds] = useState(60);
  const [advice, setAdvice] = useState(null);
  const [job, setJob] = useState(null);
  const [progress, setProgress] = useState(null);
  const [result, setResult] = useState(() => {
    try { return JSON.parse(localStorage.getItem(RESULT_KEY)) || null; } catch { return null; }
  });
  const [unassigned, setUnassigned] = useState(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState(null);
  const [live, setLive] = useState(null);      // truth from the database
  const [notice, setNotice] = useState(null);
  const pollRef = useRef(null);

  useEffect(() => () => clearInterval(pollRef.current), []);

  useEffect(() => {
    api.suggestedBudget().then((a) => {
      setAdvice(a);
      setSeconds(a.recommendedSeconds);
    }).catch(() => {});
  }, []);

  /**
   * Reads the timetable straight from the database. The stored result is only a snapshot of one
   * past run and goes stale as soon as anything changes, so the page reports this instead.
   */
  const loadLive = useCallback(async () => {
    try {
      const acts = await api.activities();
      const placed = acts.filter((a) => a.room && a.day).length;
      setLive({ placed, total: acts.length });
      setUnassigned(placed === acts.length ? [] : await api.unassignedDetails());
    } catch {
      setLive(null);
    }
  }, []);

  useEffect(() => { loadLive(); }, [loadLive]);

  function saveResult(res) {
    setResult(res);
    try { localStorage.setItem(RESULT_KEY, JSON.stringify(res)); } catch { /* quota */ }
  }

  async function start() {
    setError(null);
    setNotice(null);
    setResult(null);
    setRunning(true);
    setProgress(null);
    clearInterval(pollRef.current);   // never leave a second poller behind
    try {
      const res = await api.generate(Number(seconds));
      const jobId = res.jobId;
      if (res.alreadyRunning === 'true') {
        setNotice('O generare era deja pornită — pagina s-a reconectat la ea în loc să pornească una nouă.');
      }
      setJob(jobId);
      pollRef.current = setInterval(async () => {
        const st = await api.status(jobId);
        setProgress(st);
        if (st.state === 'COMPLETED' || st.state === 'FAILED') {
          clearInterval(pollRef.current);
          setRunning(false);
          saveResult(await api.result(jobId));
          loadLive();
        }
      }, 1500);
    } catch (e) {
      setError(e.message);
      setRunning(false);
    }
  }

  return (
    <div>
      <h1>Generare orar</h1>

      {advice && <AdvicePanel advice={advice} seconds={seconds} setSeconds={setSeconds} />}

      <div className="panel">
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <div className="field">
            <label>Buget de timp</label>
            <div className="row" style={{ gap: 8, alignItems: 'center' }}>
              <input type="number" min="5" value={seconds}
                     onChange={(e) => setSeconds(e.target.value)} style={{ width: 110 }} />
              <span className="muted">secunde ({human(Number(seconds) || 0)})</span>
            </div>
          </div>
          <button onClick={start} disabled={running}>
            {running ? 'Se generează…' : 'Generează orarul'}
          </button>
        </div>
        {running && (
          <p className="badge warn" style={{ display: 'inline-block', marginTop: 12 }}>
            Se rulează… {progress ? `${progress.total - progress.unassigned} din ${progress.total} plasate, scor ${progress.score || '—'}` : 'se pregătește'}
          </p>
        )}
        {error && <p className="badge bad" style={{ display: 'inline-block', marginTop: 12 }}>{error}</p>}
      </div>

      {notice && <p className="badge warn" style={{ display: 'inline-block' }}>{notice}</p>}

      <ResultPanel result={result} unassigned={unassigned} live={live} />

      <CompareSection />
    </div>
  );
}

/** The recommendation, the numbers behind it, and anything no budget can fix. */
function AdvicePanel({ advice, seconds, setSeconds }) {
  const [open, setOpen] = useState(false);
  const choices = [
    ['Rapid', advice.quickSeconds, 'pentru o verificare rapidă'],
    ['Recomandat', advice.recommendedSeconds, 'echilibru între timp și calitate'],
    ['Amănunțit', advice.thoroughSeconds, 'când vrei ultimele procente de calitate'],
  ];
  return (
    <div className="panel">
      <h2>Cât timp să aloci</h2>
      <p style={{ marginTop: 0 }}>{advice.summary}</p>

      <div className="row" style={{ gap: 10, marginBottom: 6 }}>
        {choices.map(([label, value, note]) => (
          <button
            key={label}
            className={Number(seconds) === value ? '' : 'secondary'}
            onClick={() => setSeconds(value)}
            style={{ flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', padding: '10px 14px' }}
            title={note}
          >
            <span style={{ fontWeight: 700 }}>{label} · {human(value)}</span>
          </button>
        ))}
      </div>
      <p className="muted">
        Recomandarea pornește de la dimensiunea și „strâmtoarea” problemei: {advice.activities} activități,
        {' '}{advice.rooms} săli, {advice.timeSlots} module, ocupare {advice.occupancyPercent}%.
      </p>

      {advice.blockers && advice.blockers.length > 0 && (
        <div style={{ marginTop: 10 }}>
          <p className="badge bad" style={{ display: 'inline-block' }}>
            {advice.blockers.length} {advice.blockers.length === 1 ? 'problemă' : 'probleme'} pe care timpul nu le rezolvă
          </p>
          <ul style={{ margin: '8px 0 0', paddingLeft: 20, fontSize: 13, lineHeight: 1.7 }}>
            {advice.blockers.map((b, i) => <li key={i}>{b}</li>)}
          </ul>
          <p className="muted" style={{ marginBottom: 0 }}>
            Astea se rezolvă din <Link to="/admin">Administrare</Link> sau <Link to="/constraints">Constrângeri</Link>,
            nu cu buget mai mare.
          </p>
        </div>
      )}

      <button className="ghost" onClick={() => setOpen(!open)} style={{ marginTop: 10, paddingLeft: 0 }}>
        {open ? '▾ Ascunde calculul' : '▸ De ce atât?'}
      </button>
      {open && (
        <ul style={{ margin: '6px 0 0', paddingLeft: 20, fontSize: 13, lineHeight: 1.7 }} className="muted">
          {advice.reasons.map((r, i) => <li key={i}>{r}</li>)}
        </ul>
      )}
    </div>
  );
}

/** Groups the per-activity diagnoses into a handful of general, actionable statements. */
function summarize(unassigned) {
  const families = new Map();
  let needsTime = 0;
  let needsData = 0;

  for (const item of unassigned) {
    if (item.structural) needsData += 1; else needsTime += 1;
    const best = item.options && item.options[0] ? item.options[0].violations : [];
    for (const v of best) {
      const key = familyOf(v);
      if (!families.has(key)) families.set(key, { count: 0, subjects: new Set() });
      const e = families.get(key);
      e.count += 1;
      e.subjects.add(item.subject);
    }
  }
  return {
    needsTime,
    needsData,
    families: [...families.entries()]
      .map(([key, e]) => ({ key, ...ADVICE[key], count: e.count, subjects: [...e.subjects] }))
      .sort((a, b) => b.count - a.count),
  };
}

function familyOf(v) {
  if (v.startsWith('capacitate')) return 'capacity';
  if (v === 'necesită amfiteatru') return 'amphi';
  if (v === 'sala indisponibilă') return 'roomUnavail';
  if (v.startsWith('master')) return 'master';
  if (v.startsWith('zi blocată')) return 'blockedDay';
  if (v.startsWith('interval rezervat')) return 'special';
  if (v.startsWith('cadrul didactic este indisponibil')) return 'profUnavail';
  if (v.startsWith('sală interzisă') || v.startsWith('cadrul didactic poate preda')) return 'profRoom';
  if (v.startsWith('sala e ocupată')) return 'roomBusy';
  if (v.startsWith('grupa are deja')) return 'groupBusy';
  if (v.startsWith('cadrul didactic predă')) return 'profBusy';
  return 'other';
}

const ADVICE = {
  capacity: {
    title: 'Sălile sunt prea mici',
    what: 'Grupele depășesc numărul de locuri din sălile rămase libere.',
    how: 'Mărește capacitatea unei săli în Administrare, împarte grupa în două, sau verifică dacă numărul de studenți e corect.',
  },
  amphi: {
    title: 'Nu mai sunt amfiteatre libere',
    what: 'Sunt cursuri marcate „Curs Amfiteatru”, dar amfiteatrele sunt deja ocupate în acele intervale.',
    how: 'Scoate cerința de amfiteatru unde nu e strict necesară, sau marchează încă o sală mare drept amfiteatru.',
  },
  roomUnavail: {
    title: 'Sălile sunt marcate indisponibile',
    what: 'Intervalele libere cad peste perioade în care sălile sunt blocate.',
    how: 'Restrânge indisponibilitățile din Constrângeri — ține minte că sălile sunt libere implicit.',
  },
  master: {
    title: 'Masteratul încape doar seara',
    what: 'Activitățile de master pot ocupa doar modulele 6–8, iar acelea sunt pline.',
    how: 'Eliberează module de seară sau verifică dacă toate grupele sunt într-adevăr de master.',
  },
  blockedDay: {
    title: 'Zilele blocate reduc spațiul',
    what: 'Anii cu zi liberă au cu o zi mai puțin la dispoziție.',
    how: 'Dacă ziua liberă nu mai e obligatorie, șterge regula din Constrângeri.',
  },
  special: {
    title: 'Intervalele rezervate reduc spațiul',
    what: 'Blocajele pentru DCT/DPPD/CCOC/limbi ocupă intervale care ar fi fost utile.',
    how: 'Verifică în Constrângeri dacă toate blocajele mai sunt valabile în acest semestru.',
  },
  profUnavail: {
    title: 'Indisponibilitățile cadrelor didactice',
    what: 'Intervalele rămase libere sunt exact cele în care cadrul didactic nu poate preda.',
    how: 'Restrânge intervalul din Constrângeri sau repartizează ora altui cadru didactic.',
  },
  profRoom: {
    title: 'Restricțiile de sală ale cadrelor didactice',
    what: 'Cadrul didactic e legat de o sală care nu era liberă, sau îi este interzisă sala disponibilă.',
    how: 'Ridică restricția din Constrângeri dacă nu mai e necesară.',
  },
  roomBusy: {
    title: 'Sălile sunt deja ocupate',
    what: 'În intervalele potrivite, toate sălile sunt luate de alte ore.',
    how: 'Adaugă o sală, sau mută alte ore ca să eliberezi intervalul.',
  },
  groupBusy: {
    title: 'Grupele au deja ore în acele intervale',
    what: 'Grupa e prea încărcată: orele ei acoperă aproape toate modulele.',
    how: 'Distribuie orele grupei pe mai multe zile sau verifică dacă toate îi aparțin într-adevăr.',
  },
  profBusy: {
    title: 'Cadrele didactice au deja ore',
    what: 'Cadrul didactic predă deja în intervalele care ar fi mers.',
    how: 'Repartizează o parte din ore altui cadru didactic.',
  },
  other: {
    title: 'Alte blocaje',
    what: 'Vezi detaliile pe fiecare oră mai jos.',
    how: 'Deschide lista de variante pentru ora respectivă.',
  },
};

function ResultPanel({ result, unassigned, live }) {
  if (!live) {
    return null;
  }
  const placed = live.placed;
  const total = live.total;
  const unplaced = total - placed;
  const allPlaced = unplaced === 0;
  const feasible = !result || result.hardScore === 0;
  const pct = total ? Math.round((placed / total) * 100) : 0;
  const summary = unassigned && unassigned.length ? summarize(unassigned) : null;
  const [showRaw, setShowRaw] = useState(false);

  if (result && result.state === 'FAILED') {
    return (
      <div className="panel">
        <h2>Rezultat</h2>
        <p className="badge bad" style={{ display: 'inline-block' }}>
          Generarea a eșuat: {result.error}
        </p>
      </div>
    );
  }

  return (
    <div className="panel">
      <h2>Starea orarului</h2>

      <div style={{
        background: allPlaced ? 'var(--ok-soft)' : 'var(--warn-soft)',
        border: `1px solid ${allPlaced ? 'var(--ok-border)' : 'var(--warn-border)'}`,
        borderRadius: 'var(--r-md)', padding: '14px 16px', marginBottom: 16,
      }}>
        <p style={{ margin: 0, fontSize: 15, lineHeight: 1.6 }}>
          {total === 0 ? (
            <>Nu există activități. Importă datele semestrului întâi.</>
          ) : allPlaced ? (
            <>✓ <b>Toate cele {total} ore sunt programate.</b> Nicio oră nu a rămas pe dinafară.</>
          ) : (
            <>
              <b>{placed} din {total} ore sunt programate</b> ({pct}%).{' '}
              <b>{unplaced} {unplaced === 1 ? 'oră a rămas neplasată' : 'ore au rămas neplasate'}</b>
              {' '}— vezi mai jos de ce și ce poți face.
            </>
          )}
        </p>
      </div>

      <div className="score-tiles">
        <Tile n={`${placed}/${total}`} l="Ore programate" />
        <Tile n={unplaced} l={unplaced === 1 ? 'Oră neplasată' : 'Ore neplasate'} />
        {result && <Tile n={feasible ? 'niciuna' : -result.hardScore} l="Reguli încălcate" />}
        {result && <Tile n={`${(result.solveMillis / 1000).toFixed(1)}s`} l="Ultima rulare" />}
      </div>

      {result ? (
        <p className="muted" style={{ marginTop: 10, marginBottom: 0 }}>
          <button className="ghost" style={{ paddingLeft: 0, fontSize: 12 }}
                  onClick={() => setShowRaw(!showRaw)}>
            {showRaw ? '▾' : '▸'} detalii despre ultima rulare
          </button>
          {showRaw && (
            <span> A durat {(result.solveMillis / 1000).toFixed(1)} s și a raportat{' '}
              {result.totalActivities - result.unassignedCount} din {result.totalActivities} ore plasate.
              Scor tehnic: {result.score} — „hard” = reguli absolute, „medium” = ore neplasate,
              „soft” = penalizări de calitate (cu cât mai aproape de 0, cu atât mai bine).
              {result.unassignedCount !== unplaced && (
                <> <b>Orarul s-a schimbat de atunci</b>, de aceea cifrele de sus diferă.</>
              )}
            </span>
          )}
        </p>
      ) : (
        <p className="muted" style={{ marginTop: 10, marginBottom: 0 }}>
          Cifrele de mai sus vin direct din baza de date, nu dintr-o rulare anterioară.
        </p>
      )}

      {summary && (
        <>
          <h2 style={{ marginTop: 22 }}>De ce nu au încăput și ce poți face</h2>
          <p className="muted" style={{ marginTop: 0 }}>
            {summary.needsTime > 0 && (
              <>{summary.needsTime} {summary.needsTime === 1 ? 'oră ar putea încăpea' : 'ore ar putea încăpea'} dacă
              mărești bugetul de timp. </>
            )}
            {summary.needsData > 0 && (
              <>{summary.needsData} {summary.needsData === 1 ? 'oră cere' : 'ore cer'} o modificare
              a datelor sau a regulilor — timpul suplimentar nu ajută.</>
            )}
          </p>
          {summary.families.map((f) => (
            <div key={f.key} style={{
              border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
              padding: '12px 14px', marginBottom: 10,
            }}>
              <div><b>{f.title}</b>{' '}
                <span className="badge warn">{f.count} {f.count === 1 ? 'oră' : 'ore'}</span>
              </div>
              <p className="muted" style={{ margin: '6px 0 4px' }}>{f.what}</p>
              <p style={{ margin: 0, fontSize: 13 }}><b>Ce poți face:</b> {f.how}</p>
              <p className="muted" style={{ margin: '6px 0 0', fontSize: 12 }}>
                Afectează: {f.subjects.slice(0, 4).join(', ')}
                {f.subjects.length > 4 && ` și încă ${f.subjects.length - 4}`}
              </p>
            </div>
          ))}
        </>
      )}

      {allPlaced && (
        <p className="muted" style={{ marginTop: 14 }}>
          Vezi orarul în <Link to="/timetable">Orar</Link> și salvează varianta în{' '}
          <Link to="/saved">Orare salvate</Link> ca să nu o pierzi la următoarea generare.
        </p>
      )}

      {unassigned && unassigned.length > 0 && (
        <>
          <h2 style={{ marginTop: 22 }}>Fiecare oră neplasată ({unassigned.length})</h2>
          <p className="muted">
            Pentru fiecare, cele mai apropiate variante — inclusiv unele care încalcă o regulă,
            dacă asta e singura ieșire.
          </p>
          {unassigned.map((u) => <UnassignedCard key={u.activityId} item={u} />)}
        </>
      )}

      {result && result.conflicts && result.conflicts.length > 0 && (
        <>
          <h2 style={{ marginTop: 22 }}>Reguli care nu au putut fi respectate complet</h2>
          <div style={{ overflowX: 'auto' }}>
            <table>
              <thead>
                <tr>
                  <th style={{ minWidth: 90 }}>Cât de grav</th>
                  <th style={{ minWidth: 220 }}>Regula</th>
                  <th style={{ minWidth: 70 }}>Cazuri</th>
                  <th style={{ minWidth: 240 }}>Ce înseamnă</th>
                  <th style={{ minWidth: 240 }}>Ce poți face</th>
                </tr>
              </thead>
              <tbody>
                {result.conflicts.map((c, i) => (
                  <tr key={i}>
                    <td>
                      <span className={`badge ${c.severity === 'HARD' ? 'bad' : 'warn'}`}>
                        {c.severity === 'HARD' ? 'încălcată' : 'a blocat ore'}
                      </span>
                    </td>
                    <td><b>{c.label || c.constraint}</b></td>
                    <td>{c.matchCount}</td>
                    <td className="muted">{c.meaning}</td>
                    <td className="muted">{c.suggestion}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}

function UnassignedCard({ item }) {
  const [open, setOpen] = useState(false);
  return (
    <div style={{
      border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
      padding: '12px 14px', marginBottom: 10, background: 'var(--panel-2)',
    }}>
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'baseline' }}>
        <div>
          <b>{item.subject}</b>{' '}
          <span className="muted">({item.subjectCode})</span>{' '}
          <span className="badge">{TYPE_RO[item.activityType] || item.activityType}</span>
        </div>
        <span className={`badge ${item.structural ? 'bad' : 'warn'}`}>
          {item.structural ? 'necesită modificarea datelor' : 'ar putea încăpea cu mai mult timp'}
        </span>
      </div>
      <p className="muted" style={{ margin: '6px 0' }}>
        {item.professor || 'fără cadru didactic'} · {item.groups.join(', ') || 'fără grupă'} · {item.students} studenți
      </p>
      <p style={{ margin: '6px 0', fontSize: 13 }}>{item.reason}</p>

      <button className="ghost" style={{ paddingLeft: 0 }} onClick={() => setOpen(!open)}>
        {open ? '▾ Ascunde variantele' : `▸ Cele mai apropiate ${item.options.length} variante`}
      </button>
      {open && (
        <div style={{ overflowX: 'auto', marginTop: 8 }}>
          <table>
            <thead>
              <tr><th>Ziua</th><th>Intervalul</th><th>Sala</th><th>Ce ar încălca</th></tr>
            </thead>
            <tbody>
              {item.options.map((o, i) => (
                <tr key={i}>
                  <td>{DAY_RO[o.day] || o.day}</td>
                  <td>Modulul {o.slotIndex} · {o.time}</td>
                  <td>{o.room}</td>
                  <td>
                    {o.violations.length === 0
                      ? <span className="badge ok">nimic — variantă validă</span>
                      : o.violations.map((v, j) => (
                          <span key={j} className="badge warn" style={{ marginRight: 4 }}>{v}</span>
                        ))}
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

/** Kept, but folded away: useful once in a while, noise the rest of the time. */
function CompareSection() {
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('30, 120, 300');
  const [rows, setRows] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function run() {
    setBusy(true); setRows(null); setError(null);
    try {
      const budgets = input.split(',').map((s) => Number(s.trim())).filter((n) => n > 0);
      setRows(await api.compare(budgets));
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  return (
    <div className="panel">
      <button className="ghost" style={{ paddingLeft: 0 }} onClick={() => setOpen(!open)}>
        {open ? '▾' : '▸'} Compară mai multe bugete de timp
      </button>
      {!open && (
        <p className="muted" style={{ margin: '6px 0 0' }}>
          Rulează aceeași problemă cu mai multe bugete, ca să vezi cât ajută timpul în plus.
          Rareori necesar — recomandarea de sus acoperă cazul obișnuit.
        </p>
      )}
      {open && (
        <>
          <p className="muted" style={{ marginTop: 6 }}>
            Atenție: rulează generările una după alta, deci durează cât suma bugetelor.
          </p>
          <div className="row">
            <div className="field">
              <label>Bugete (secunde, separate prin virgulă)</label>
              <input value={input} onChange={(e) => setInput(e.target.value)} style={{ width: 220 }} />
            </div>
            <button className="secondary" onClick={run} disabled={busy}>
              {busy ? 'Se rulează…' : 'Compară'}
            </button>
          </div>
          {error && <p className="badge bad" style={{ display: 'inline-block', marginTop: 10 }}>{error}</p>}
          {rows && (
            <div style={{ overflowX: 'auto', marginTop: 12 }}>
              <table>
                <thead>
                  <tr><th>Buget</th><th>Timp real</th><th>Plasate</th><th>Neplasate</th><th>Încălcări hard</th><th>Scor</th></tr>
                </thead>
                <tbody>
                  {rows.map((r, i) => (
                    <tr key={i}>
                      <td>{human(r.budgetSeconds)}</td>
                      <td>{(r.actualMillis / 1000).toFixed(1)}s</td>
                      <td>{r.unassigned === 0 ? <span className="badge ok">toate</span> : '—'}</td>
                      <td>{r.unassigned}</td>
                      <td>{r.violatedHardConstraints}</td>
                      <td className="muted">{r.score}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function Tile({ n, l }) {
  return <div className="tile"><div className="n">{n}</div><div className="l">{l}</div></div>;
}
