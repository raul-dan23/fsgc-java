import { useState } from 'react';
import { api } from '../api/client.js';

export default function ImportPage() {
  const [drag, setDrag] = useState(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  async function upload(file) {
    if (!file) return;
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const res = await api.importExcel(file);
      setResult(res.body);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  function onDrop(e) {
    e.preventDefault();
    setDrag(false);
    upload(e.dataTransfer.files[0]);
  }

  return (
    <div>
      <h1>Import semestru (.xlsx)</h1>
      <div className="panel">
        <div
          className={`dropzone ${drag ? 'drag' : ''}`}
          onDragOver={(e) => { e.preventDefault(); setDrag(true); }}
          onDragLeave={() => setDrag(false)}
          onDrop={onDrop}
        >
          <p>Trage fișierul Excel aici (sheet-uri: Sectii, Profesori, Sali, Discipline)</p>
          <p className="muted">sau</p>
          <input type="file" accept=".xlsx" onChange={(e) => upload(e.target.files[0])} />
        </div>
        {busy && <p className="muted">Se importă…</p>}
        {error && <p className="badge bad">Eroare: {error}</p>}
      </div>

      {result && (
        <div className="panel">
          <h2>
            Rezultat{' '}
            <span className={`badge ${result.success ? 'ok' : 'bad'}`}>
              {result.success ? 'Succes' : 'Eșuat (rollback)'}
            </span>
          </h2>
          <div className="score-tiles">
            <Tile n={result.studentGroups} l="Grupe" />
            <Tile n={result.professors} l="Profesori" />
            <Tile n={result.rooms} l="Săli" />
            <Tile n={result.roomAvailabilities} l="Disponibilități săli" />
            <Tile n={result.subjects} l="Discipline" />
            <Tile n={result.activities} l="Activități" />
          </div>

          {result.errors.length > 0 && (
            <>
              <h2 style={{ marginTop: 18 }}>Erori ({result.errors.length})</h2>
              <IssueTable issues={result.errors} cls="bad" />
            </>
          )}
          {result.warnings.length > 0 && (
            <>
              <h2 style={{ marginTop: 18 }}>Avertismente ({result.warnings.length})</h2>
              <IssueTable issues={result.warnings} cls="warn" />
            </>
          )}
          {result.success && (
            <p className="muted">
              Importul a reușit. Treci la <b>Constrângeri</b> (opțional) și apoi <b>Generare</b>.
            </p>
          )}
        </div>
      )}
    </div>
  );
}

function Tile({ n, l }) {
  return (
    <div className="tile">
      <div className="n">{n}</div>
      <div className="l">{l}</div>
    </div>
  );
}

function IssueTable({ issues, cls }) {
  return (
    <table>
      <thead>
        <tr><th>Sheet</th><th>Rând</th><th>Mesaj</th></tr>
      </thead>
      <tbody>
        {issues.map((i, idx) => (
          <tr key={idx}>
            <td>{i.sheet}</td>
            <td><span className={`badge ${cls}`}>{i.row}</span></td>
            <td>{i.message}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
