import { NavLink, Route, Routes, Navigate } from 'react-router-dom';
import ImportPage from './pages/ImportPage.jsx';
import ConstraintsPage from './pages/ConstraintsPage.jsx';
import GeneratePage from './pages/GeneratePage.jsx';
import TimetablePage from './pages/TimetablePage.jsx';

export default function App() {
  return (
    <div>
      <nav className="nav">
        <span className="brand">Orar FSGC</span>
        <NavLink to="/import">Import</NavLink>
        <NavLink to="/constraints">Constrângeri</NavLink>
        <NavLink to="/generate">Generare</NavLink>
        <NavLink to="/timetable">Orar</NavLink>
      </nav>
      <div className="container">
        <Routes>
          <Route path="/" element={<Navigate to="/import" replace />} />
          <Route path="/import" element={<ImportPage />} />
          <Route path="/constraints" element={<ConstraintsPage />} />
          <Route path="/generate" element={<GeneratePage />} />
          <Route path="/timetable" element={<TimetablePage />} />
        </Routes>
      </div>
    </div>
  );
}
