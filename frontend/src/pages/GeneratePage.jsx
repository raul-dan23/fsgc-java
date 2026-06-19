import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client.js';

const RESULT_KEY = 'orar.lastResult';

export default function GeneratePage() {
  const [seconds, setSeconds] = useState(60);
  const [job, setJob] = useState(null);
  // restore the last generation result so navigating away and back doesn't lose it
  const [result, setResult] = useState(() => {
    try { return JSON.parse(localStorage.getItem(RESULT_KEY)) || null; } catch { return null; }
  });
  const [running, setRunning] = useState(false);
  const [compareInput, setCompareInput] = useState('30, 120, 300');
  const [compareRows, setCompareRows] = useState(null);
  const [comparing, setComparing] = useState(false);
  const [error, setError] = useState(null);
  const pollRef = useRef(null);

  useEffect(() => () => clearInterval(pollRef.current), []);

  function saveResult(res) {
    setResult(res);
    try { localStorage.setItem(RESULT_KEY, JSON.stringify(res)); } catch { /* ignore quota */ }
  }

  async function start() {
    setError(null);
    setResult(null);
    setRunning(true);
    try {
      const { jobId } = await api.generate(Number(seconds));
      setJob(jobId);
      pollRef.current = setInterval(async () => {
        const st = await api.status(jobId);
        setJob(jobId);
        if (st.state === 'COMPLETED' || st.state === 'FAILED') {
          clearInterval(pollRef.current);
          setRunning(false);
          saveResult(await api.result(jobId));
        }
      }, 1500);
    } catch (e) {
      setError(e.message);
      setRunning(false);
    }
  }

  async function runCompare() {
    setComparing(true);
    setCompareRows(null);
    setError(null);
    try {
      const budgets = compareInput.split(',').map((s) => Number(s.trim())).filter((n) => n > 0);
      setCompareRows(await api.compare(budgets));
    } catch (e) {
      setError(e.message);
    } finally {
      setComparing(false);
    }
  }

  return (
    <div>
      <h1>Generare orar</h1>
      <div className="panel">
        <div className="row">
          <div className="field">
            <label>Buget de timp (secunde)</label>
            <input type="number" min="5" value={seconds}
                   onChange={(e) => setSeconds(e.target.value)} style={{ width: 120 }} />
          </div>
          <button onClick={start} disabled={running}>
            {running ? 'Se generează…' : 'Generează orar'}
          </button>
        </div>
        <p className="muted">
          Bugetul mic (ex. 15–30s) e pentru prezentări live; pentru calitate maximă folosește mai mult timp.
        </p>
        {running && <p className="badge warn">Job {job} în lucru… (se actualizează automat)</p>}
        {error && <p className="badge bad">{error}</p>}
      </div>

      {result && <ResultPanel result={result} />}

      <div className="panel">
        <h2>Compară bugete de timp</h2>
        <div className="row">
          <div className="field">
            <label>Bugete (secunde, separate prin virgulă)</label>
            <input value={compareInput} onChange={(e) => setCompareInput(e.target.value)} style={{ width: 220 }} />
          </div>
          <button className="secondary" onClick={runCompare} disabled={comparing}>
            {comparing ? 'Se rulează…' : 'Compară'}
          </button>
        </div>
        {comparing && <p className="muted">Se rulează succesiv fiecare buget… poate dura.</p>}
        {compareRows && (
          <table style={{ marginTop: 12 }}>
            <thead>
              <tr><th>Buget (s)</th><th>Timp real</th><th>Scor</th><th>Hard</th><th>Neplasate</th><th>Constr. hard încălcate</th></tr>
            </thead>
            <tbody>
              {compareRows.map((r, i) => (
                <tr key={i}>
                  <td>{r.budgetSeconds}</td>
                  <td>{(r.actualMillis / 1000).toFixed(1)}s</td>
                  <td>{r.score}</td>
                  <td>{r.hardScore}</td>
                  <td>{r.unassigned}</td>
                  <td>{r.violatedHardConstraints}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function ResultPanel({ result }) {
  const feasible = result.hardScore === 0;
  return (
    <div className="panel">
      <h2>
        Rezultat{' '}
        <span className={`badge ${result.state === 'COMPLETED' ? 'ok' : 'bad'}`}>{result.state}</span>{' '}
        <span className={`badge ${feasible ? 'ok' : 'bad'}`}>
          {feasible ? 'Fezabil (0 hard)' : `${-result.hardScore} încălcări hard`}
        </span>
      </h2>
      {result.error && <p className="badge bad">{result.error}</p>}
      <div className="score-tiles">
        <Tile n={result.score} l="Scor" />
        <Tile n={result.totalActivities} l="Activități" />
        <Tile n={result.unassignedCount} l="Neplasate" />
        <Tile n={`${(result.solveMillis / 1000).toFixed(1)}s`} l="Timp" />
      </div>
      {result.conflicts && result.conflicts.length > 0 && (
        <>
          <h2 style={{ marginTop: 18 }}>Conflicte / probleme</h2>
          <table>
            <thead><tr><th>Severitate</th><th>Constrângere</th><th>Nr.</th><th>Sugestie</th></tr></thead>
            <tbody>
              {result.conflicts.map((c, i) => (
                <tr key={i}>
                  <td><span className={`badge ${c.severity === 'HARD' ? 'bad' : 'warn'}`}>{c.severity}</span></td>
                  <td>{c.constraint}</td>
                  <td>{c.matchCount}</td>
                  <td>{c.suggestion}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
      <p className="muted" style={{ marginTop: 12 }}>
        Vezi orarul complet în pagina <b>Orar</b>.
      </p>
    </div>
  );
}

function Tile({ n, l }) {
  return <div className="tile"><div className="n">{n}</div><div className="l">{l}</div></div>;
}
