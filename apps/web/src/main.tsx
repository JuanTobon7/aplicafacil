import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Route, Routes } from "react-router";
import { AuthProvider } from "./auth/AuthContext";
import { RequireAuth } from "./auth/RequireAuth";
import { Layout } from "./components/Layout";
import { WelcomePage } from "./pages/WelcomePage";
import { ConnectPage } from "./pages/ConnectPage";
import { AuthCallbackPage } from "./pages/AuthCallbackPage";
import { ProfilePage } from "./pages/ProfilePage";
import { AiProvidersPage } from "./pages/AiProvidersPage";
import { JobSearchPage } from "./pages/JobSearchPage";
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
            <Route path="perfil" element={<RequireAuth><ProfilePage /></RequireAuth>} />
            <Route path="ia" element={<RequireAuth><AiProvidersPage /></RequireAuth>} />
            <Route path="vacantes" element={<RequireAuth><JobSearchPage /></RequireAuth>} />
            <Route path="*" element={<p>Página no encontrada.</p>} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
