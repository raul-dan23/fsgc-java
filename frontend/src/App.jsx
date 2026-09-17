import { Link, NavLink, Route, Routes, Navigate } from 'react-router-dom';
import ImportPage from './pages/ImportPage.jsx';
import ConstraintsPage from './pages/ConstraintsPage.jsx';
import GeneratePage from './pages/GeneratePage.jsx';
import TimetablePage from './pages/TimetablePage.jsx';
import AdminPage from './pages/AdminPage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import SavedTimetablesPage from './pages/SavedTimetablesPage.jsx';

export default function App() {
  return (
    <div>
      <nav className="nav">
        <Link to="/" className="brand">
          <span className="logo">◷</span>
          <span>Orar&nbsp;FSGC<br /><span className="sub">Generator orar</span></span>
        </Link>
        <NavLink to="/" end>Acasă</NavLink>
        <NavLink to="/import">Import</NavLink>
        <NavLink to="/constraints">Constrângeri</NavLink>
        <NavLink to="/generate">Generare</NavLink>
        <NavLink to="/timetable">Orar</NavLink>
        <NavLink to="/saved">Orare salvate</NavLink>
        <NavLink to="/admin">Administrare</NavLink>
        <span className="spacer" />
      </nav>
      <div className="container">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/import" element={<ImportPage />} />
          <Route path="/constraints" element={<ConstraintsPage />} />
          <Route path="/generate" element={<GeneratePage />} />
          <Route path="/timetable" element={<TimetablePage />} />
          <Route path="/saved" element={<SavedTimetablesPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
    </div>
  );
}
