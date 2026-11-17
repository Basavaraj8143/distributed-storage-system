import { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { clearCredentials, isAuthenticated, setUnauthorizedHandler } from "./api";
import AppShell from "./components/AppShell";
import DashboardPage from "./pages/DashboardPage";
import UploadPage from "./pages/UploadPage";
import FilesPage from "./pages/FilesPage";
import NodeStatusPage from "./pages/NodeStatusPage";
import LogsPage from "./pages/LogsPage";
import SimulationPage from "./pages/SimulationPage";
import NotFoundPage from "./pages/NotFoundPage";
import LoginPage from "./pages/LoginPage";

export default function App() {
  const [signedIn, setSignedIn] = useState(isAuthenticated());
  const [notice, setNotice] = useState("");

  useEffect(() => {
    // Any request the master answers with 401 (for example after a password change)
    // sends the user back to the login page.
    setUnauthorizedHandler(() => {
      setNotice("Your sign-in is no longer valid. Please sign in again.");
      setSignedIn(false);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  function signOut() {
    clearCredentials();
    setNotice("");
    setSignedIn(false);
  }

  if (!signedIn) {
    return (
      <LoginPage
        notice={notice}
        onSuccess={() => {
          setNotice("");
          setSignedIn(true);
        }}
      />
    );
  }

  return (
    <AppShell onSignOut={signOut}>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/upload" element={<UploadPage />} />
        <Route path="/files" element={<FilesPage />} />
        <Route path="/nodes" element={<NodeStatusPage />} />
        <Route path="/logs" element={<LogsPage />} />
        <Route path="/simulation" element={<SimulationPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppShell>
  );
}
