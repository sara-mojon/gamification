import { useAuth } from "react-oidc-context";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Layout from "./components/Layout";
import Retos from "./pages/Retos";
import Perfil from "./pages/Perfil";
import Ranking from "./pages/Ranking";
import Entrenar from "./pages/Entrenar";

function App() {
  const auth = useAuth();

  // 1. Pantalla de carga mientras Keycloak decide si hay sesión
  if (auth.isLoading) {
    return <div style={{ padding: "2rem", textAlign: "center", fontFamily: "sans-serif" }}>Cargando la matriz...</div>;
  }

  // 2. Si ocurre algún error de conexión con Keycloak
  if (auth.error) {
    return <div style={{ padding: "2rem", color: "red", fontFamily: "sans-serif" }}>Ocurrió un error: {auth.error.message}</div>;
  }

  // 3. PANTALLA PRIVADA (El usuario está logueado)
  if (auth.isAuthenticated) {
    return (
      <BrowserRouter>
        <Routes>
          {/* El Layout envuelve a todas las páginas privadas */}
          <Route path="/" element={<Layout />}>
            
            {/* Si entras a la raíz ("/"), te redirige automáticamente a los retos */}
            <Route index element={<Navigate to="/retos" replace />} />
            
            {/* Las tres secciones principales de tu app */}
            <Route path="retos" element={<Retos />} />
            <Route path="perfil" element={<Perfil />} />
            <Route path="ranking" element={<Ranking />} />
            <Route path="entrenar/:id" element={<Entrenar />} />
            
          </Route>
        </Routes>
      </BrowserRouter>
    );
  }

  // 4. PANTALLA PÚBLICA (El usuario NO está logueado)
  return (
    <div style={{ height: "100vh", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", backgroundColor: "#f5f5f5", fontFamily: "sans-serif" }}>
      <h1 style={{ fontSize: "3rem", marginBottom: "10px", color: "#333" }}>Codewars Clone</h1>
      <p style={{ marginBottom: "30px", color: "#666", fontSize: "1.2rem" }}>Demuestra tus habilidades de programación.</p>
      
      <button 
        onClick={() => void auth.signinRedirect()}
        style={{ 
          padding: "15px 30px", 
          fontSize: "1.2rem", 
          cursor: "pointer", 
          backgroundColor: "#ff4b4b", 
          color: "white", 
          border: "none", 
          borderRadius: "8px", 
          fontWeight: "bold", 
          boxShadow: "0 4px 6px rgba(0,0,0,0.1)",
          transition: "transform 0.1s, background-color 0.2s"
        }}
        onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#d43f3f"}
        onMouseOut={(e) => e.currentTarget.style.backgroundColor = "#ff4b4b"}
        onMouseDown={(e) => e.currentTarget.style.transform = "scale(0.95)"}
        onMouseUp={(e) => e.currentTarget.style.transform = "scale(1)"}
      >
        Entrar a la Plataforma
      </button>
    </div>
  );
}

export default App;