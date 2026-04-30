// frontend/src/pages/Perfil.tsx

import { useState, useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { User, Calendar, Code, Star, Activity, AlertTriangle, Settings, ChevronLeft, ChevronRight } from "lucide-react";
import toast from 'react-hot-toast';

interface HistorialReto {
  id: number;
  titulo: string;
  fecha: string;
  puntosGanados: number;
  dificultad: string;
}

interface PerfilData {
  puntos: number;
  retosCompletados: number;
  rachaDias: number;
  rangoGlobal: number | string;
}

export default function Perfil() {
  const auth = useAuth();
  const token = auth.user?.access_token;
  
  const username = auth.user?.profile.preferred_username || "Hacker Anónimo";
  const email = auth.user?.profile.email || "correo@oculto.com";

  const baseUrlUser = import.meta.env.VITE_USER_URL || 'http://localhost:8080';
  const baseUrlChallenges = import.meta.env.VITE_CHALLENGES_URL || 'http://localhost:8081';
  const keycloakAccountUrl = import.meta.env.VITE_KEYCLOAK_ACCOUNT_URL || 'https://auth.saramg.org/realms/masorange-realm/account/';

  const [estadisticas, setEstadisticas] = useState<PerfilData>({
    puntos: 0,
    retosCompletados: 0,
    rachaDias: 0, 
    rangoGlobal: "-" 
  });
  
  const [historial, setHistorial] = useState<HistorialReto[]>([]);
  const [paginaActividad, setPaginaActividad] = useState(0); 
  const [totalPaginasActividad, setTotalPaginasActividad] = useState(1);
  const [lenguajePreferido, setLenguajePreferido] = useState("java"); 
  const [userId, setUserId] = useState<number | null>(null);
  const [cargando, setCargando] = useState(true);
  const [mostrarModal, setMostrarModal] = useState(false);

  useEffect(() => {
    const cargarMiPerfil = async () => {
      if (!token) return;

      try {
        const response = await fetch(`${baseUrlUser}/api/users/me`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });

        let posicionRanking: number | string = "-";
        try {
          const rankResponse = await fetch(`${baseUrlUser}/api/users/ranking/${username}`, {
            headers: { 'Authorization': `Bearer ${token}` }
          });
          if (rankResponse.ok) {
            const rankData = await rankResponse.json();
            posicionRanking = rankData.position;
          }
        } catch (error) {
          console.error("No se pudo obtener el ranking", error);
        }

        if (response.ok) {
          const misDatos = await response.json();
          setUserId(misDatos.id);

          setEstadisticas({
            puntos: misDatos.score || 0,
            retosCompletados: misDatos.retosCompletados || 0,
            rachaDias: misDatos.currentStreak || 0,
            rangoGlobal: posicionRanking
          });

          if (misDatos.preferredLanguage) { 
            setLenguajePreferido(misDatos.preferredLanguage);
          }
        }
      } catch (error) {
        console.error("Error conectando con el backend:", error);
      } finally {
        setCargando(false);
      }
    };

    cargarMiPerfil();
  }, [token, baseUrlUser, username]);

  useEffect(() => {
    const cargarHistorial = async () => {
      if (!token) return;
      try {
        const historyRes = await fetch(`${baseUrlChallenges}/api/challenges/me/history?page=${paginaActividad}&size=6`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (historyRes.ok) {
          const historyData = await historyRes.json();
          setHistorial(historyData.content); 
          setTotalPaginasActividad(historyData.totalPages);
        }
      } catch (error) { 
        console.error("No se pudo obtener el historial de retos:", error); 
      }
    };

    cargarHistorial();
  }, [token, baseUrlChallenges, paginaActividad]);

  const cambiarLenguaje = async (e: React.ChangeEvent<HTMLSelectElement>) => {
    const nuevoLenguaje = e.target.value;
    setLenguajePreferido(nuevoLenguaje);

    if (!userId) return;

    const peticion = fetch(`${baseUrlUser}/api/users/${userId}`, {
      method: 'PATCH',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ preferredLanguage: nuevoLenguaje })
    }).then(res => {
      if (!res.ok) throw new Error("Error al guardar");
      return res;
    });

    toast.promise(peticion, {
      loading: 'Guardando preferencia...',
      success: '¡Lenguaje preferido actualizado! ',
      error: 'Error al actualizar el lenguaje ',
    }, { style: { backgroundColor: '#1e1e1e', color: '#fff' } });
  };

  const confirmarEliminacion = async () => {
    if (!userId) return;
    
    setMostrarModal(false);

    try {
      const response = await fetch(`${baseUrlUser}/api/users/${userId}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (response.ok) {
        toast.success("Tu cuenta ha sido eliminada. Cerrando sesión...");
        setTimeout(() => auth.removeUser(), 2000); 
      } else {
        toast.error("Hubo un problema al eliminar la cuenta.");
      }
    } catch {
      toast.error("Error de red intentando conectar con el servidor.");
    }
  };

  if (cargando) {
    return <div style={{ textAlign: "center", padding: "50px", color: "#666" }}>Cargando tu perfil...</div>;
  }

  return (
    <>
      <div style={{ maxWidth: "1000px", margin: "0 auto", position: "relative" }}>
        
        <div style={{ marginBottom: "30px" }}>
          <h1 style={{ fontSize: "2.5rem", color: "#1e1e1e", marginBottom: "10px", display: "flex", alignItems: "center" }}>
            <User size={36} style={{ marginRight: "15px", color: "#ff4b4b" }} />
            Mi Perfil
          </h1>
        </div>

        <div style={{ display: "flex", gap: "30px", flexWrap: "wrap", alignItems: "flex-start" }}>
        
          {/* COLUMNA IZQUIERDA */}
          <div style={{ flex: "1 1 300px", display: "flex", flexDirection: "column", gap: "30px" }}>
            
            {/* Tarjeta de Identidad */}
            <div style={{ backgroundColor: "white", borderRadius: "10px", padding: "30px", boxShadow: "0 4px 6px rgba(0,0,0,0.05)", border: "1px solid #eaeaea", textAlign: "center" }}>
              <div style={{ 
                width: "100px", height: "100px", borderRadius: "50%", backgroundColor: "#1e1e1e", color: "#ff4b4b", 
                display: "flex", alignItems: "center", justifyContent: "center", fontSize: "3rem", fontWeight: "bold", 
                margin: "0 auto 20px auto", border: "4px solid #fff", boxShadow: "0 4px 10px rgba(0,0,0,0.1)"
              }}>
                {username.charAt(0).toUpperCase()}
              </div>
              <h2 style={{ margin: "0 0 5px 0", fontSize: "1.8rem", color: "#333" }}>{username}</h2>
              <p style={{ margin: "0 0 20px 0", color: "#888", fontSize: "0.95rem" }}>{email}</p>
              
              <a 
                href={keycloakAccountUrl} 
                target="_blank" 
                rel="noopener noreferrer"
                style={{ 
                  display: "inline-flex", alignItems: "center", gap: "8px", padding: "8px 16px", 
                  backgroundColor: "#f0f0f0", color: "#333", borderRadius: "6px", textDecoration: "none", 
                  fontSize: "0.9rem", fontWeight: "bold", border: "1px solid #ddd", transition: "all 0.2s"
                }}
                onMouseOver={(e) => { e.currentTarget.style.backgroundColor = "#e4e4e4"; e.currentTarget.style.borderColor = "#ccc"; }}
                onMouseOut={(e) => { e.currentTarget.style.backgroundColor = "#f0f0f0"; e.currentTarget.style.borderColor = "#ddd"; }}
              >
                <Settings size={16} /> Gestionar Cuenta (Contraseña/Email)
              </a>
              
              <div style={{ marginTop: "25px", padding: "10px", backgroundColor: "#f9f9f9", borderRadius: "8px", display: "inline-block", border: "1px solid #eee" }}>
                <span style={{ color: "#555", fontSize: "0.9rem", fontWeight: "bold" }}>Rango Actual:</span>
                <span style={{ marginLeft: "10px", color: "#ff4b4b", fontWeight: "bold", fontSize: "1.1rem" }}> {estadisticas.rangoGlobal} </span>
              </div>
            </div>

            <div style={{ backgroundColor: "white", borderRadius: "10px", padding: "25px", boxShadow: "0 4px 6px rgba(0,0,0,0.05)", border: "1px solid #eaeaea" }}>
              <h3 style={{ margin: "0 0 20px 0", fontSize: "1.2rem", color: "#333", borderBottom: "2px solid #f0f0f0", paddingBottom: "10px" }}>Estadísticas de Combate</h3>
              
              <div style={{ display: "flex", flexDirection: "column", gap: "15px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div style={{ display: "flex", alignItems: "center", color: "#555" }}><Star size={18} style={{ marginRight: "10px", color: "#FFD700" }} /> Puntos de Honor</div>
                  <strong style={{ fontSize: "1.2rem", color: "#1e1e1e" }}>{estadisticas.puntos.toLocaleString()}</strong>
                </div>
                
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div style={{ display: "flex", alignItems: "center", color: "#555" }}><Code size={18} style={{ marginRight: "10px", color: "#4caf50" }} /> Retos Resueltos</div>
                  <strong style={{ fontSize: "1.2rem", color: "#1e1e1e" }}>{estadisticas.retosCompletados}</strong>
                </div>
                
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div style={{ display: "flex", alignItems: "center", color: "#555" }}><Activity size={18} style={{ marginRight: "10px", color: "#ff9800" }} /> Racha de días</div>
                  <strong style={{ fontSize: "1.2rem", color: "#1e1e1e" }}>{estadisticas.rachaDias}</strong>
                </div>
              </div>
            </div>

            <div style={{ backgroundColor: "white", borderRadius: "10px", padding: "25px", boxShadow: "0 4px 6px rgba(0,0,0,0.05)", border: "1px solid #eaeaea" }}>
              <h3 style={{ margin: "0 0 15px 0", fontSize: "1.2rem", color: "#333", borderBottom: "2px solid #f0f0f0", paddingBottom: "10px" }}>Preferencias</h3>
              
              <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                <label style={{ color: "#555", fontSize: "0.95rem", fontWeight: "bold" }}>Lenguaje de programación:</label>
                <select 
                  value={lenguajePreferido}
                  onChange={cambiarLenguaje}
                  style={{ 
                    padding: "10px", borderRadius: "6px", border: "1px solid #ccc", 
                    backgroundColor: "#f9f9f9", fontSize: "1rem", outline: "none", cursor: "pointer",
                    width: "100%"
                  }}
                >
                  <option value="javascript">JavaScript</option>
                  <option value="python">Python</option>
                  <option value="java">Java</option>
                  <option value="c">C</option>
                </select>
              </div>
            </div>

            {/* BOTÓN DE ELIMINAR CUENTA */}
            <button 
              onClick={() => setMostrarModal(true)}
              style={{ 
                width: "100%", padding: "15px", borderRadius: "8px", 
                backgroundColor: "#e53935", color: "white", border: "none",
                fontWeight: "bold", cursor: "pointer", transition: "background-color 0.2s",
                display: "flex", justifyContent: "center", alignItems: "center", gap: "10px"
              }}
              onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#c62828"}
              onMouseOut={(e) => e.currentTarget.style.backgroundColor = "#e53935"}
            >
              <AlertTriangle size={18} /> Eliminar mi cuenta
            </button>

          </div>

          {/* COLUMNA DERECHA (Historial) */}
          <div style={{ flex: "2 1 500px", backgroundColor: "white", borderRadius: "10px", padding: "30px", boxShadow: "0 4px 6px rgba(0,0,0,0.05)", border: "1px solid #eaeaea", alignSelf: "flex-start" }}>
            
            <h3 style={{ margin: "0 0 25px 0", fontSize: "1.4rem", color: "#333", display: "flex", alignItems: "center" }}>
              <Calendar size={24} style={{ marginRight: "10px", color: "#888" }} />
              Actividad Reciente
            </h3>

            <div style={{ display: "flex", flexDirection: "column", gap: "15px" }}>
              {historial.length > 0 ? (
                historial.map((item) => (
                  <div key={item.id} style={{ 
                    display: "flex", justifyContent: "space-between", alignItems: "center", 
                    padding: "20px", borderRadius: "8px", border: "1px solid #eee",
                    transition: "background-color 0.2s"
                  }}
                  onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#fcfcfc"}
                  onMouseOut={(e) => e.currentTarget.style.backgroundColor = "transparent"}
                  >
                    <div>
                      <h4 style={{ margin: "0 0 5px 0", fontSize: "1.1rem", color: "#333" }}>{item.titulo}</h4>
                      <div style={{ display: "flex", gap: "15px", alignItems: "center", fontSize: "0.9rem", color: "#888" }}>
                        <span>{item.fecha}</span>
                        <span style={{ 
                          backgroundColor: item.dificultad.includes("Fácil") ? "#e8f5e9" : item.dificultad.includes("Normal") ? "#fff3e0" : "#ffebee", 
                          color: item.dificultad.includes("Fácil") ? "#2e7d32" : item.dificultad.includes("Normal") ? "#ef6c00" : "#c62828", 
                          padding: "2px 8px", borderRadius: "10px", fontWeight: "bold", fontSize: "0.8rem"
                        }}>
                          {item.dificultad}
                        </span>
                      </div>
                    </div>
                    
                    <div style={{ textAlign: "right" }}>
                      <span style={{ display: "block", color: "#4caf50", fontWeight: "bold", fontSize: "1.2rem" }}>
                        +{item.puntosGanados} px
                      </span>
                    </div>
                  </div>
                ))
              ) : (
                <div style={{ textAlign: "center", padding: "40px", color: "#aaa", backgroundColor: "#fcfcfc", borderRadius: "8px", border: "1px dashed #ddd" }}>
                  <Code size={48} style={{ margin: "0 auto 10px auto", opacity: 0.3, display: "block" }} />
                  <p style={{ margin: 0 }}>Aún no has resuelto ningún reto.</p>
                  <p style={{ margin: "5px 0 0 0", fontWeight: "bold", color: "#888" }}>¡Empieza tu primera Kata!</p>
                </div>
              )}
            </div>

            {/* CONTROLES DE PAGINACIÓN */}
            {totalPaginasActividad > 1 && (
              <div style={{ display: "flex", justifyContent: "center", alignItems: "center", marginTop: "25px", gap: "20px" }}>
                <button 
                  onClick={() => setPaginaActividad(p => Math.max(0, p - 1))} 
                  disabled={paginaActividad === 0} 
                  style={{ display: "flex", alignItems: "center", padding: "10px 15px", borderRadius: "6px", border: "1px solid #ddd", backgroundColor: paginaActividad === 0 ? "#f5f5f5" : "white", color: paginaActividad === 0 ? "#aaa" : "#333", cursor: paginaActividad === 0 ? "not-allowed" : "pointer", transition: "0.2s" }}
                >
                  <ChevronLeft size={18} style={{ marginRight: "5px" }} /> Anterior
                </button>
                <span style={{ fontSize: "1rem", color: "#555", fontWeight: "500" }}>
                  Página {paginaActividad + 1} de {totalPaginasActividad}
                </span>
                <button 
                  onClick={() => setPaginaActividad(p => Math.min(totalPaginasActividad - 1, p + 1))} 
                  disabled={paginaActividad === totalPaginasActividad - 1} 
                  style={{ display: "flex", alignItems: "center", padding: "10px 15px", borderRadius: "6px", border: "1px solid #ddd", backgroundColor: paginaActividad === totalPaginasActividad - 1 ? "#f5f5f5" : "white", color: paginaActividad === totalPaginasActividad - 1 ? "#aaa" : "#333", cursor: paginaActividad === totalPaginasActividad - 1 ? "not-allowed" : "pointer", transition: "0.2s" }}
                >
                  Siguiente <ChevronRight size={18} style={{ marginLeft: "5px" }} />
                </button>
              </div>
            )}

          </div>

        </div>
      </div>

      {/* --- MODAL DE CONFIRMACIÓN --- */}
      {mostrarModal && (
        <div style={{
          position: "fixed", top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: "rgba(0, 0, 0, 0.6)", zIndex: 1000,
          display: "flex", justifyContent: "center", alignItems: "center",
          backdropFilter: "blur(4px)"
        }}>
          <div style={{
            backgroundColor: "white", padding: "40px", borderRadius: "12px",
            maxWidth: "450px", width: "90%", textAlign: "center",
            boxShadow: "0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)"
          }}>
            
            <AlertTriangle size={60} color="#e53935" style={{ margin: "0 auto 20px auto" }} />
            
            <h2 style={{ margin: "0 0 15px 0", color: "#1e1e1e", fontSize: "1.5rem" }}>¿Estás seguro?</h2>
            
            <p style={{ color: "#666", fontSize: "1.05rem", lineHeight: "1.6", marginBottom: "30px" }}>
              Al eliminar tu cuenta, perderás todo tu progreso, puntos de honor y retos resueltos. <strong>Esta acción es irreversible.</strong>
            </p>
            
            <div style={{ display: "flex", gap: "15px", justifyContent: "center" }}>
              <button 
                onClick={() => setMostrarModal(false)}
                style={{
                  flex: 1, padding: "12px", borderRadius: "8px", border: "1px solid #ddd",
                  backgroundColor: "white", color: "#555", fontWeight: "bold", cursor: "pointer",
                  transition: "background-color 0.2s"
                }}
                onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#f5f5f5"}
                onMouseOut={(e) => e.currentTarget.style.backgroundColor = "white"}
              >
                No, cancelar
              </button>
              
              <button 
                onClick={confirmarEliminacion}
                style={{
                  flex: 1, padding: "12px", borderRadius: "8px", border: "none",
                  backgroundColor: "#e53935", color: "white", fontWeight: "bold", cursor: "pointer",
                  transition: "background-color 0.2s"
                }}
                onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#c62828"}
                onMouseOut={(e) => e.currentTarget.style.backgroundColor = "#e53935"}
              >
                Sí, eliminar
              </button>
            </div>

          </div>
        </div>
      )}
    </>
  );
}