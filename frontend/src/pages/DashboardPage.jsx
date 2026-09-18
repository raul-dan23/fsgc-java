import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';

/**
 * Landing page: one card per feature, plus a live read of what is currently in the database so
 * the state of the workflow (imported? generated? saved?) is visible before clicking anything.
 */
export default function DashboardPage() {
  const [summary, setSummary] = useState(null);
  const [saved, setSaved] = useState(null);
  const [schedule, setSchedule] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let alive = true;
    Promise.all([
      api.adminSummary().catch(() => null),
      api.savedTimetables().catch(() => null),
      api.activities().catch(() => null),
    ]).then(([s, sv, acts]) => {
      if (!alive) return;
      setSummary(s);
      setSaved(sv);
      setSchedule(acts);
      setFailed(!s);
    });
    return () => { alive = false; };
  }, []);

  const hasData = !!summary && summary.activities > 0;
  const assigned = schedule ? schedule.filter((a) => a.assigned).length : 0;
  const total = schedule ? schedule.length : 0;
  const hasSchedule = total > 0 && assigned > 0;

  const cards = [
    {
      to: '/import',
      icon: '⭳',
      title: 'Import date semestru',
      text: 'Încarcă fișierul Excel cu secții, profesori, săli și discipline.',
      status: hasData
        ? `${summary.subjects} discipline, ${summary.groups} grupe`
        : 'Nicio dată importată',
      tone: hasData ? 'ok' : 'warn',
    },
    {
      to: '/constraints',
      icon: '⚖',
      title: 'Constrângeri',
      text: 'Ponderi pentru calitatea orarului, zile blocate, indisponibilități.',
      status: 'Opțional, înainte de generare',
      tone: 'neutral',
    },
    {
      to: '/generate',
      icon: '▶',
      title: 'Generare',
      text: 'Rulează solverul și obține o variantă de orar.',
      status: hasData ? 'Gata de rulat' : 'Necesită date importate',
      tone: hasData ? 'ok' : 'warn',
    },
    {
      to: '/timetable',
      icon: '▦',
      title: 'Orar',
      text: 'Vizualizează orarul curent și mută activități manual.',
      status: hasSchedule ? `${assigned} din ${total} activități plasate` : 'Niciun orar generat',
      tone: hasSchedule ? 'ok' : 'warn',
    },
    {
      to: '/saved',
      icon: '🗂',
      title: 'Orare salvate',
      text: 'Istoricul variantelor păstrate. Poți reveni oricând la una.',
      status: saved === null
        ? '—'
        : saved.length === 0 ? 'Niciun orar salvat' : `${saved.length} salvate`,
      tone: saved && saved.length > 0 ? 'ok' : 'neutral',
    },
    {
      to: '/admin',
      icon: '⚙',
      title: 'Administrare',
      text: 'Editează direct grupele, profesorii, sălile, disciplinele și activitățile.',
      status: hasData ? `${summary.professors} profesori, ${summary.rooms} săli` : '—',
      tone: 'neutral',
    },
  ];

  return (
    <div>
      <h1>Orar FSGC</h1>
      <p className="muted" style={{ marginTop: -10, marginBottom: 22 }}>
        Generator de orar pentru Facultatea de Științe Politice, Filosofie și Științe ale Comunicării.
      </p>

      {failed && (
        <div className="panel">
          <p className="badge bad" style={{ display: 'inline-block' }}>
            Backendul nu răspunde. Verifică dacă rulează containerul <code>orar-backend</code>.
          </p>
        </div>
      )}

      <div className="dash-grid">
        {cards.map((c) => (
          <Link key={c.to} to={c.to} className="dash-card">
            <span className="dash-icon" aria-hidden="true">{c.icon}</span>
            <span className="dash-title">{c.title}</span>
            <span className="dash-text">{c.text}</span>
            <span className={`badge ${c.tone === 'neutral' ? '' : c.tone}`}>{c.status}</span>
          </Link>
        ))}
      </div>

      <div className="panel" style={{ marginTop: 22 }}>
        <h2>Ordinea obișnuită</h2>
        <p className="muted" style={{ marginBottom: 0 }}>
          <b>Import date</b> → (opțional) <b>Constrângeri</b> → <b>Generare</b> → <b>Orar</b> pentru
          ajustări manuale → salvează varianta bună în <b>Orare salvate</b>.
          Corecturile punctuale pe date se fac în <b>Administrare</b>, fără a reimporta Excelul.
        </p>
      </div>
    </div>
  );
}
