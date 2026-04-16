// frontend/src/main.tsx

import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import './index.css'
import { AuthProvider } from 'react-oidc-context'

// 1. URL de Keycloak dinámica al estilo Vite (con fallback para local)
const keycloakUrl = import.meta.env.VITE_KEYCLOAK_URL || "http://localhost:8180";

// 2. Detectamos automáticamente dónde estamos ejecutando la web
const frontendUrl = window.location.origin;

const oidcConfig = {
  authority: `${keycloakUrl}/realms/masorange-realm`,
  client_id: "gamification-client",
  redirect_uri: frontendUrl,
  post_logout_redirect_uri: frontendUrl,
  
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
    const rutaDestino = sessionStorage.getItem('rutaDestino');

    if (rutaDestino) {
      sessionStorage.removeItem('rutaDestino');
      window.location.href = rutaDestino;
    }
  }
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    { }
    <AuthProvider {...oidcConfig}>
      <App />
    </AuthProvider>
  </React.StrictMode>,
)