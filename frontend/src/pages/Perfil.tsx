import { useAuth } from "react-oidc-context";
import { User, Code, Star, Calendar, Activity } from "lucide-react";

// DATOS FALSOS (Historial de actividad reciente)
const actividadReciente = [
  { id: 101, titulo: "Sumar dos números", fecha: "Hoy, 10:30", puntosGanados: 10, dificultad: "Fácil" },
  { id: 102, titulo: "Contar vocales", fecha: "Ayer, 18:15", puntosGanados: 15, dificultad: "Fácil" },
  { id: 103, titulo: "Invertir una cadena de texto", fecha: "Ayer, 17:40", puntosGanados: 25, dificultad: "Normal" },
  { id: 104, titulo: "Detector de Palíndromos", fecha: "Hace 3 días", puntosGanados: 15, dificultad: "Fácil" },
];

export default function Perfil() {
  // Extraemos los datos REALES de tu sesión de Keycloak
  const auth = useAuth();
  const username = auth.user?.profile.preferred_username || "Hacker Anónimo";
  const email = auth.user?.profile.email || "correo@oculto.com";

  // Estadísticas mockeadas (coinciden con tu posición en el Ranking)
  const estadisticas = {
    puntos: 1250,
    rangoGlobal: 7,
    retosCompletados: 42,
    rachaDias: 5
  };

  return (
    <div style={{ maxWidth: "1000px", margin: "0 auto" }}>
      
      {/* Cabecera de la página */}
      <div style={{ marginBottom: "30px" }}>
        <h1 style={{ fontSize: "2.5rem", color: "#1e1e1e", marginBottom: "10px", display: "flex", alignItems: "center" }}>
          <User size={36} style={{ marginRight: "15px", color: "#ff4b4b" }} />
          Mi Perfil
        </h1>
      </div>

      <div style={{ display: "flex", gap: "30px", flexWrap: "wrap", alignItems: "flex-start" }}>
        
        {/* --- COLUMNA IZQUIERDA (Info del usuario y Tarjeta de Stats) --- */}
        <div style={{ flex: "1 1 300px", display: "flex", flexDirection: "column", gap: "30px" }}>
          
          {/* Tarjeta de Identidad */}
          <div style={{ backgroundColor: "white", borderRadius: "10px", padding: "30px", boxShadow: "0 4px 6px rgba(0,0,0,0.05)", border: "1px solid #eaeaea", textAlign: "center" }}>
            <div style={{ 
              width: "100px", height: "100px", borderRadius: "50%", backgroundColor: "#1e1e1e", color: "#ff4b4b", 
              display: "flex", alignItems: "center", justifyContent: "center", fontSize: "3rem", fontWeight: "bold", 
              margin: "0 auto 20px auto", border: "4px solid #fff", boxShadow: "0 4px 10px rgba(0,0,0,0.1)"
            }}>
              {/* Cogemos la primera letra del nombre para el Avatar */}
              {username.charAt(0).toUpperCase()}
            </div>
            <h2 style={{ margin: "0 0 5px 0", fontSize: "1.8rem", color: "#333" }}>{username}</h2>
            <p style={{ margin: 0, color: "#888", fontSize: "0.95rem" }}>{email}</p>
            
            <div style={{ marginTop: "20px", padding: "10px", backgroundColor: "#f9f9f9", borderRadius: "8px", display: "inline-block", border: "1px solid #eee" }}>
              <span style={{ color: "#555", fontSize: "0.9rem", fontWeight: "bold" }}>Rango Actual:</span>
              <span style={{ marginLeft: "10px", color: "#ff4b4b", fontWeight: "bold", fontSize: "1.1rem" }}># {estadisticas.rangoGlobal} del mundo</span>
            </div>
          </div>

          {/* Tarjeta de Estadísticas Rápidas */}
          <div style={{ backgroundColor: "white", borderRadius: "10px", padding: "25px", boxShadow: "0 4px 6px rgba(0,0,0,0.05)", border: "1px solid #eaeaea" }}>
            <h3 style={{ margin: "0 0 20px 0", fontSize: "1.2rem", color: "#333", borderBottom: "2px solid #f0f0f0", paddingBottom: "10px" }}>Estadísticas de Combate</h3>
            
            <div style={{ display: "flex", flexDirection: "column", gap: "15px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div style={{ display: "flex", alignItems: "center", color: "#555" }}><Star size={18} style={{ marginRight: "10px", color: "#FFD700" }} /> Puntos de Honor</div>
                <strong style={{ fontSize: "1.2rem", color: "#1e1e1e" }}>{estadisticas.puntos.toLocaleString()}</strong>
              </div>
              
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div style={{ display: "flex", alignItems: "center", color: "#555" }}><Code size={18} style={{ marginRight: "10px", color: "#4caf50" }} /> Katas Resueltas</div>
                <strong style={{ fontSize: "1.2rem", color: "#1e1e1e" }}>{estadisticas.retosCompletados}</strong>
              </div>
              
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div style={{ display: "flex", alignItems: "center", color: "#555" }}><Activity size={18} style={{ marginRight: "10px", color: "#ff9800" }} /> Racha de días</div>
                <strong style={{ fontSize: "1.2rem", color: "#1e1e1e" }}>{estadisticas.rachaDias} 🔥</strong>
              </div>
            </div>
          </div>

        </div>

        {/* --- COLUMNA DERECHA (Historial de Actividad) --- */}
        <div style={{ flex: "2 1 500px", backgroundColor: "white", borderRadius: "10px", padding: "30px", boxShadow: "0 4px 6px rgba(0,0,0,0.05)", border: "1px solid #eaeaea" }}>
          <h3 style={{ margin: "0 0 25px 0", fontSize: "1.4rem", color: "#333", display: "flex", alignItems: "center" }}>
            <Calendar size={24} style={{ marginRight: "10px", color: "#888" }} />
            Actividad Reciente
          </h3>

          <div style={{ display: "flex", flexDirection: "column", gap: "15px" }}>
            {actividadReciente.map((actividad) => (
              <div key={actividad.id} style={{ 
                display: "flex", justifyContent: "space-between", alignItems: "center", 
                padding: "20px", borderRadius: "8px", border: "1px solid #eee",
                transition: "background-color 0.2s"
              }}
              onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#fcfcfc"}
              onMouseOut={(e) => e.currentTarget.style.backgroundColor = "transparent"}
              >
                <div>
                  <h4 style={{ margin: "0 0 5px 0", fontSize: "1.1rem", color: "#333" }}>{actividad.titulo}</h4>
                  <div style={{ display: "flex", gap: "15px", alignItems: "center", fontSize: "0.9rem", color: "#888" }}>
                    <span>{actividad.fecha}</span>
                    <span style={{ 
                      backgroundColor: actividad.dificultad === "Fácil" ? "#e8f5e9" : "#fff3e0", 
                      color: actividad.dificultad === "Fácil" ? "#2e7d32" : "#ef6c00", 
                      padding: "2px 8px", borderRadius: "10px", fontWeight: "bold", fontSize: "0.8rem"
                    }}>
                      {actividad.dificultad}
                    </span>
                  </div>
                </div>
                
                <div style={{ textAlign: "right" }}>
                  <span style={{ display: "block", color: "#4caf50", fontWeight: "bold", fontSize: "1.2rem" }}>
                    +{actividad.puntosGanados} px
                  </span>
                </div>
              </div>
            ))}
          </div>

          <button style={{ 
            width: "100%", padding: "15px", marginTop: "20px", borderRadius: "8px", 
            backgroundColor: "#f9f9f9", border: "1px solid #ddd", color: "#666", 
            fontWeight: "bold", cursor: "pointer", transition: "background-color 0.2s"
          }}
          onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#eee"}
          onMouseOut={(e) => e.currentTarget.style.backgroundColor = "#f9f9f9"}
          >
            Ver todo el historial
          </button>
        </div>

      </div>
    </div>
  );
}