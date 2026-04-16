// frontend/src/pages/Ranking.tsx

import { useState, useEffect } from "react";
import { Trophy, Search, ChevronLeft, ChevronRight, Medal, ArrowUpDown } from "lucide-react";
import { useAuth } from "react-oidc-context"; 

const ITEMS_POR_PAGINA = 10;

interface BackendUser {
  id: number;
  username: string;
  nombre: string;
  score: number;
  retosCompletados?: number; 
}

interface FrontendUser {
  id: number;
  nombre: string;
  puntos: number;
  retos: number;
  posicionGlobal?: number;
}

export default function Ranking() {
  const auth = useAuth();
  const token = auth.user?.access_token;

  const [usuarios, setUsuarios] = useState<FrontendUser[]>([]);
  const [cargando, setCargando] = useState(true);

  const [busqueda, setBusqueda] = useState("");
  const [criterioOrden, setCriterioOrden] = useState("puntos"); 
  const [paginaActual, setPaginaActual] = useState(1);

  // --- VARIABLE DE ENTORNO DINÁMICA ---
  const baseUrl = import.meta.env.VITE_USER_URL || 'http://localhost:8080';

  useEffect(() => {
    const cargarUsuarios = async () => {
      if (!token) return;

      try {
        const response = await fetch(`${baseUrl}/api/users`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        });

        if (response.ok) {
          const datosBackend: BackendUser[] = await response.json();
          
          const datosAdaptados: FrontendUser[] = datosBackend.map((u) => ({
            id: u.id,
            nombre: u.username || u.nombre , 
            puntos: u.score || 0,
            retos: u.retosCompletados || 0 
          }));

          setUsuarios(datosAdaptados);
        } else {
          console.error("Error al cargar ranking:", response.status);
        }
      } catch (error) {
        console.error("Error conectando con el backend:", error);
      } finally {
        setCargando(false);
      }
    };

    cargarUsuarios();
  }, [token, baseUrl]);
  
  const usuariosOrdenados = [...usuarios]
    .sort((a, b) => {
      if (criterioOrden === "puntos") return b.puntos - a.puntos;
      if (criterioOrden === "retos") return b.retos - a.retos;
      return 0;
    })
    .map((usuario, index) => ({
      ...usuario,
      posicionGlobal: index + 1
    }));

  const usuariosFiltrados = usuariosOrdenados.filter((usuario) =>
    usuario.nombre.toLowerCase().includes(busqueda.toLowerCase())
  );

  const totalPaginas = Math.ceil(usuariosFiltrados.length / ITEMS_POR_PAGINA) || 1;
  const indiceUltimoItem = paginaActual * ITEMS_POR_PAGINA;
  const indicePrimerItem = indiceUltimoItem - ITEMS_POR_PAGINA;
  const usuariosPaginados = usuariosFiltrados.slice(indicePrimerItem, indiceUltimoItem);

  const obtenerEstiloPosicion = (posicion: number) => {
    if (posicion === 1) return { color: "#FFD700", bg: "#FFF9C4" };
    if (posicion === 2) return { color: "#C0C0C0", bg: "#F5F5F5" };
    if (posicion === 3) return { color: "#CD7F32", bg: "#EFEBE9" };
    return { color: "#666", bg: "#f0f0f0" };
  };

  if (cargando) {
    return <div style={{ textAlign: "center", padding: "50px", color: "#666" }}>Cargando ranking mundial... 🏆</div>;
  }

  return (
    <div style={{ maxWidth: "1000px", margin: "0 auto" }}>
      
      <div style={{ marginBottom: "30px" }}>
        <h1 style={{ fontSize: "2.5rem", color: "#1e1e1e", marginBottom: "10px", display: "flex", alignItems: "center" }}>
          <Trophy size={36} style={{ marginRight: "15px", color: "#ff4b4b" }} />
          Clasificación Global
        </h1>
        <p style={{ color: "#666", fontSize: "1.1rem" }}>
          Compite con otros desarrolladores y llega a lo más alto del ranking.
        </p>
      </div>

      <div style={{ 
        display: "flex", gap: "15px", marginBottom: "30px", 
        backgroundColor: "white", padding: "15px", borderRadius: "10px", 
        boxShadow: "0 2px 5px rgba(0,0,0,0.05)", border: "1px solid #eaeaea",
        justifyContent: "space-between", flexWrap: "wrap"
      }}>
        
        <div style={{ flex: "1 1 300px", position: "relative" }}>
          <Search size={20} style={{ position: "absolute", left: "15px", top: "12px", color: "#888" }} />
          <input 
            type="text" 
            placeholder="Buscar por nombre de usuario..." 
            value={busqueda}
            onChange={(e) => {
              setBusqueda(e.target.value);
              setPaginaActual(1);
            }}
            style={{ width: "100%", padding: "12px 15px 12px 45px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none" }}
          />
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <ArrowUpDown size={20} color="#888" />
          <span style={{ color: "#555", fontWeight: "500" }}>Ordenar por:</span>
          <select 
            value={criterioOrden} 
            onChange={(e) => {
              setCriterioOrden(e.target.value);
              setPaginaActual(1);
            }}
            style={{ padding: "12px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none", cursor: "pointer", backgroundColor: "white" }}
          >
            <option value="puntos">Puntos Totales</option>
            <option value="retos">Retos Completados</option>
          </select>
        </div>
      </div>

      <div style={{ backgroundColor: "white", borderRadius: "10px", boxShadow: "0 4px 6px rgba(0,0,0,0.05)", border: "1px solid #eaeaea", overflow: "hidden" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left" }}>
          <thead>
            <tr style={{ backgroundColor: "#1e1e1e", color: "white" }}>
              <th style={{ padding: "20px", width: "100px", textAlign: "center" }}>Rank</th>
              <th style={{ padding: "20px" }}>Usuario</th>
              <th style={{ padding: "20px", textAlign: "center" }}>Retos</th>
              <th style={{ padding: "20px", textAlign: "right" }}>Puntos</th>
            </tr>
          </thead>
          <tbody>
            {usuariosFiltrados.length === 0 ? (
              <tr>
                <td colSpan={4} style={{ padding: "40px", textAlign: "center", color: "#888" }}>
                  No se ha encontrado a ningún usuario con ese nombre.
                </td>
              </tr>
            ) : (
              usuariosPaginados.map((usuario) => {
                const estilo = obtenerEstiloPosicion(usuario.posicionGlobal!);
                return (
                  <tr key={usuario.id} style={{ borderBottom: "1px solid #eee", transition: "background-color 0.2s" }} onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#f9f9f9"} onMouseOut={(e) => e.currentTarget.style.backgroundColor = "transparent"}>
                    
                    <td style={{ padding: "15px 20px", textAlign: "center" }}>
                      <div style={{ 
                        display: "inline-flex", alignItems: "center", justifyContent: "center",
                        width: "35px", height: "35px", borderRadius: "50%", 
                        backgroundColor: estilo.bg, color: estilo.color, 
                        fontWeight: "bold", fontSize: "1.1rem"
                      }}>
                        {usuario.posicionGlobal! <= 3 ? <Medal size={20} /> : `#${usuario.posicionGlobal}`}
                      </div>
                    </td>
                    
                    <td style={{ padding: "15px 20px", fontWeight: "bold", color: "#333", fontSize: "1.1rem" }}>
                      {usuario.nombre}
                    </td>
                    
                    <td style={{ padding: "15px 20px", textAlign: "center", color: "#666" }}>
                      {usuario.retos}
                    </td>
                    
                    <td style={{ padding: "15px 20px", textAlign: "right", fontWeight: "bold", color: "#ff4b4b", fontSize: "1.1rem" }}>
                      {usuario.puntos.toLocaleString()} px
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {totalPaginas > 1 && (
        <div style={{ display: "flex", justifyContent: "center", alignItems: "center", marginTop: "30px", gap: "20px" }}>
          <button 
            onClick={() => setPaginaActual(p => p - 1)} 
            disabled={paginaActual === 1}
            style={{ 
              display: "flex", alignItems: "center", padding: "10px 15px", borderRadius: "6px", border: "1px solid #ddd", 
              backgroundColor: paginaActual === 1 ? "#f5f5f5" : "white", color: paginaActual === 1 ? "#aaa" : "#333", 
              cursor: paginaActual === 1 ? "not-allowed" : "pointer"
            }}
          >
            <ChevronLeft size={18} style={{ marginRight: "5px" }} /> Anterior
          </button>

          <span style={{ fontSize: "1rem", color: "#555", fontWeight: "500" }}>
            Página {paginaActual} de {totalPaginas}
          </span>

          <button 
            onClick={() => setPaginaActual(p => p + 1)} 
            disabled={paginaActual === totalPaginas}
            style={{ 
              display: "flex", alignItems: "center", padding: "10px 15px", borderRadius: "6px", border: "1px solid #ddd", 
              backgroundColor: paginaActual === totalPaginas ? "#f5f5f5" : "white", color: paginaActual === totalPaginas ? "#aaa" : "#333", 
              cursor: paginaActual === totalPaginas ? "not-allowed" : "pointer"
            }}
          >
            Siguiente <ChevronRight size={18} style={{ marginLeft: "5px" }} />
          </button>
        </div>
      )}
      
    </div>
  );
}