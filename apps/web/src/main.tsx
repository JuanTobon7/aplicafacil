import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Route, Routes } from "react-router";
import { AuthProvider } from "./auth/AuthContext";
import { RequireAuth } from "./auth/RequireAuth";
import { Layout } from "./components/Layout";
import { WelcomePage } from "./pages/WelcomePage";
import { ConnectPage } from "./pages/ConnectPage";
import { AuthCallbackPage } from "./pages/AuthCallbackPage";
import { AccountsPage } from "./pages/AccountsPage";
import { AiProvidersPage } from "./pages/AiProvidersPage";
import { CvPage } from "./pages/CvPage";
import { JobSearchPage } from "./pages/JobSearchPage";
import { ManualSearchPage } from "./pages/ManualSearchPage";
import { SearchPreferencesPage } from "./pages/SearchPreferencesPage";
import { WorkProfilePage } from "./pages/WorkProfilePage";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<WelcomePage />} />
            <Route path="conectar" element={<ConnectPage />} />
            <Route path="auth/callback" element={<AuthCallbackPage />} />
            <Route path="vacantes" element={<RequireAuth><JobSearchPage /></RequireAuth>} />
            <Route path="vacantes/manual" element={<RequireAuth><ManualSearchPage /></RequireAuth>} />
            <Route path="perfil" element={<RequireAuth><WorkProfilePage /></RequireAuth>} />
            <Route path="perfil/cv" element={<RequireAuth><CvPage /></RequireAuth>} />
            <Route path="perfil/busqueda" element={<RequireAuth><SearchPreferencesPage /></RequireAuth>} />
            <Route path="cuentas" element={<RequireAuth><AccountsPage /></RequireAuth>} />
            <Route path="ia" element={<RequireAuth><AiProvidersPage /></RequireAuth>} />
            <Route path="*" element={<p>Página no encontrada.</p>} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
