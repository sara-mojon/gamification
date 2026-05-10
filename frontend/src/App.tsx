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

  if (auth.isLoading) {
    return null;
  }

  if (auth.error) {
    return <div style={{ padding: "2rem", color: "red", fontFamily: "sans-serif" }}>Ocurrió un error: {auth.error.message}</div>;
  }

  if (auth.isAuthenticated) {
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

  return (
    <div style={{ height: "100vh", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", backgroundColor: "#f5f5f5", fontFamily: "sans-serif" }}>
      <h1 style={{ fontSize: "3rem", marginBottom: "10px", color: "#333" }}>Ofelia Code</h1>
      <p style={{ marginBottom: "30px", color: "#666", fontSize: "1.2rem" }}>Demuestra tus habilidades de programación.</p>
      
      <button 
        onClick={() => {
          // 1. Apuntamos la URL actual en el Session Storage
          sessionStorage.setItem('rutaDestino', window.location.pathname);
          // 2. Redirigimos al Login
          void auth.signinRedirect();
        }}
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