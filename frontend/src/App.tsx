import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { AppLayout } from "./components/layout/AppLayout";
import { LogoMark } from "./components/ui/Icons";
import { useAuth } from "./hooks/useAuth";
import { MetaProvider } from "./hooks/useMeta";
import Dashboard from "./pages/Dashboard";
import LogActivity, { EditActivity } from "./pages/LogActivity";
import Login from "./pages/Login";
import NotFound from "./pages/NotFound";
import RunDetail from "./pages/RunDetail";
import Runs from "./pages/Runs";
import Statistics from "./pages/Statistics";
import Training from "./pages/Training";
import TrainingBlockPage from "./pages/TrainingBlock";
import TrainingBlockFormPage from "./pages/TrainingBlockForm";

function RequireAuth() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) {
    return (
      <div role="status" className="grid min-h-dvh place-items-center">
        <span className="animate-pulse">
          <LogoMark size={48} />
        </span>
        <span className="sr-only">Loading Pacebook…</span>
      </div>
    );
  }
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return (
    <MetaProvider>
      <AppLayout />
    </MetaProvider>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<RequireAuth />}>
        <Route index element={<Dashboard />} />
        <Route path="training" element={<Training />} />
        <Route path="training/new" element={<TrainingBlockFormPage />} />
        <Route path="training/:id" element={<TrainingBlockPage />} />
        <Route path="training/:id/edit" element={<TrainingBlockFormPage />} />
        <Route path="log" element={<LogActivity />} />
        <Route path="runs" element={<Runs />} />
        <Route path="runs/:id" element={<RunDetail />} />
        <Route path="runs/:id/edit" element={<EditActivity />} />
        <Route path="stats" element={<Statistics />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
