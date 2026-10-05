import { NavLink, Route, Routes } from "react-router-dom";
import ReleaseList from "./pages/ReleaseList.jsx";
import ReleaseForm from "./pages/ReleaseForm.jsx";
import BriefPage from "./pages/BriefPage.jsx";
import FinalBriefPage from "./pages/FinalBriefPage.jsx";
import ComparePage from "./pages/ComparePage.jsx";

export default function App() {
  return (
    <>
      <header className="topbar">
        <div className="brand">Release Brief Assistant</div>
        <nav>
          <NavLink to="/" end>Releases</NavLink>
          <NavLink to="/new">New release</NavLink>
          <NavLink to="/compare">Compare</NavLink>
        </nav>
        <span className="mode" title="The AI never approves or deploys a release.">AI drafts, you approve</span>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<ReleaseList />} />
          <Route path="/new" element={<ReleaseForm />} />
          <Route path="/releases/:id" element={<BriefPage />} />
          <Route path="/releases/:id/final" element={<FinalBriefPage />} />
          <Route path="/compare" element={<ComparePage />} />
        </Routes>
      </main>
    </>
  );
}