// frontend/src/App.tsx

import { useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Layout from "./components/Layout";
import Retos from "./pages/Retos";
import Perfil from "./pages/Perfil";
import Ranking from "./pages/Ranking";
import Entrenar from "./pages/Entrenar";
import Admin from "./pages/Admin";

function App() {
  const auth = useAuth();

  // 1. Extraemos las variables específicas que necesitamos para que el linter no se queje
  const { isLoading, isAuthenticated, error, signinRedirect } = auth;

  // 2. Efecto para redirigir automáticamente al login si no hay sesión
  useEffect(() => {
    if (!isLoading && !isAuthenticated && !error) {
      // Apuntamos la URL actual por si intentaba entrar a una ruta específica
      sessionStorage.setItem('rutaDestino', window.location.pathname);
      // Redirección forzosa al Login de Keycloak
      void signinRedirect();
    }
  }, [isLoading, isAuthenticated, error, signinRedirect]);

  // 3. Mientras carga o redirige, no mostramos nada (pantalla en blanco o podrías poner un spinner)
  if (isLoading || !isAuthenticated) {
    return null;
  }

  // 4. Si ocurre un error de conexión con Keycloak
  if (error) {
    return (
      <div style={{ padding: "2rem", color: "red", fontFamily: "sans-serif" }}>
        Ocurrió un error de autenticación: {error.message}
      </div>
    );
  }

  // 5. Si está autenticado, mostramos la aplicación normal con sus rutas
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          
          <Route index element={<Navigate to="/retos" replace />} />
          
          <Route path="retos" element={<Retos />} />
          <Route path="perfil" element={<Perfil />} />
          <Route path="ranking" element={<Ranking />} />
          <Route path="entrenar/:id" element={<Entrenar />} />
          <Route path="admin" element={<Admin />} />
          
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;