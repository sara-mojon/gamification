import { useState } from "react";
import { useAuth } from "react-oidc-context";
import { ShieldAlert, Download, CheckCircle, XCircle } from "lucide-react";

export default function Admin() {
  const auth = useAuth();
  const token = auth.user?.access_token;
  
  const [codewarsId, setCodewarsId] = useState("");
  const [estado, setEstado] = useState<"idle" | "loading" | "success" | "error">("idle");
  const [mensaje, setMensaje] = useState("");

const importarReto = async () => {
    // Limpiamos espacios accidentales al copiar y pegar
    const id = codewarsId.trim();
    if (!id) return;
    
    setEstado("loading");
    setMensaje("Importando reto desde la API de Codewars...");

    try {
      const response = await fetch(`http://localhost:8081/api/challenges/id/${id}`, {
        method: "GET",
        headers: {
          "Authorization": `Bearer ${token}`,
        }
      });

      if (response.ok) {
        setEstado("success");
        setMensaje("Reto importado y guardado en la base de datos con éxito");
        setCodewarsId("");
      } else {
        setEstado("error");
        setMensaje("Error al importar. Comprueba que el ID sea correcto y no exista ya.");
      }
    } catch {
      setEstado("error");
      setMensaje("Error de red intentando conectar con el backend de retos.");
    }
  };

  return (
    <div style={{ maxWidth: "800px", margin: "0 auto" }}>
      <div style={{ marginBottom: "30px" }}>
        <h1 style={{ fontSize: "2.5rem", color: "#1e1e1e", marginBottom: "10px", display: "flex", alignItems: "center" }}>
          <ShieldAlert size={36} style={{ marginRight: "15px", color: "#ff9800" }} />
          Panel de Administración
        </h1>
        <p style={{ color: "#666", fontSize: "1.1rem" }}>
          Zona de acceso restringido. Aquí puedes gestionar la plataforma.
        </p>
      </div>

      <div style={{ backgroundColor: "white", borderRadius: "10px", padding: "30px", boxShadow: "0 4px 6px rgba(0,0,0,0.05)", border: "1px solid #eaeaea" }}>
        <h2 style={{ fontSize: "1.5rem", marginTop: 0, marginBottom: "20px", color: "#333", borderBottom: "2px solid #f0f0f0", paddingBottom: "10px" }}>
          Importar Reto (Codewars)
        </h2>
        
        <p style={{ color: "#555", marginBottom: "20px" }}>
          Introduce el ID o el "slug" del reto de Codewars.
        </p>

        <div style={{ display: "flex", gap: "15px" }}>
          <input 
            type="text" 
            placeholder="Pega aquí el ID del reto..." 
            value={codewarsId}
            onChange={(e) => setCodewarsId(e.target.value)}
            disabled={estado === "loading"}
            style={{ flex: 1, padding: "12px 15px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none" }}
          />
          <button 
            onClick={importarReto}
            disabled={estado === "loading" || !codewarsId.trim()}
            style={{ 
              display: "flex", alignItems: "center", gap: "10px", padding: "0 25px", 
              backgroundColor: estado === "loading" ? "#ccc" : "#1e1e1e", color: "white", 
              border: "none", borderRadius: "6px", fontSize: "1rem", fontWeight: "bold", cursor: estado === "loading" ? "not-allowed" : "pointer" 
            }}
          >
            <Download size={20} />
            {estado === "loading" ? "Importando..." : "Importar"}
          </button>
        </div>

        {estado === "success" && (
          <div style={{ marginTop: "20px", padding: "15px", backgroundColor: "#e8f5e9", color: "#2e7d32", borderRadius: "6px", display: "flex", alignItems: "center", gap: "10px", fontWeight: "bold" }}>
            <CheckCircle size={20} /> {mensaje}
          </div>
        )}
        
        {estado === "error" && (
          <div style={{ marginTop: "20px", padding: "15px", backgroundColor: "#ffebee", color: "#c62828", borderRadius: "6px", display: "flex", alignItems: "center", gap: "10px", fontWeight: "bold" }}>
            <XCircle size={20} /> {mensaje}
          </div>
        )}
      </div>

    </div>
  );
}