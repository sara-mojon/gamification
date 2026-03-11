import { useState } from "react";
import { Trophy, Search, ChevronLeft, ChevronRight, Medal, ArrowUpDown } from "lucide-react";

// DATOS FALSOS (He creado 15 usuarios para que podamos ver la página 2 en acción)
const usuariosFalsos = [
  { id: 1, nombre: "hacker_man", puntos: 3000, retos: 100 },
  { id: 2, nombre: "fullstack_fox", puntos: 2500, retos: 90 },
  { id: 3, nombre: "python_pro", puntos: 2100, retos: 80 },
  { id: 4, nombre: "java_junkie", puntos: 1800, retos: 75 },
  { id: 5, nombre: "bug_hunter", puntos: 1500, retos: 60 },
  { id: 6, nombre: "sql_slayer", puntos: 1300, retos: 50 },
  { id: 7, nombre: "saram", puntos: 1250, retos: 42 },
  { id: 8, nombre: "dev_ninja", puntos: 1100, retos: 38 },
  { id: 9, nombre: "code_master", puntos: 1050, retos: 35 },
  { id: 10, nombre: "react_ranger", puntos: 950, retos: 33 },
  { id: 11, nombre: "backend_beast", puntos: 900, retos: 28 },
  { id: 12, nombre: "frontend_fairy", puntos: 850, retos: 30 },
  { id: 13, nombre: "css_wizard", puntos: 700, retos: 20 },
  { id: 14, nombre: "docker_dude", puntos: 600, retos: 18 },
  { id: 15, nombre: "rookie99", puntos: 200, retos: 5 },
];

const ITEMS_POR_PAGINA = 10;

export default function Ranking() {
  const [busqueda, setBusqueda] = useState("");
  const [criterioOrden, setCriterioOrden] = useState("puntos"); // "puntos" o "retos"
  const [paginaActual, setPaginaActual] = useState(1);

  // --- LÓGICA DE CLASIFICACIÓN ---
  
  // 1. Primero, ordenamos a TODOS los usuarios según el criterio elegido (y les asignamos su rango global)
  const usuariosOrdenados = [...usuariosFalsos]
    .sort((a, b) => {
      if (criterioOrden === "puntos") return b.puntos - a.puntos;
      if (criterioOrden === "retos") return b.retos - a.retos;
      return 0;
    })
    .map((usuario, index) => ({
      ...usuario,
      posicionGlobal: index + 1 // Guardamos su posición real para que si buscas "saram" siga saliendo la #7
    }));

  // 2. Luego, filtramos por el buscador de texto
  const usuariosFiltrados = usuariosOrdenados.filter((usuario) =>
    usuario.nombre.toLowerCase().includes(busqueda.toLowerCase())
  );

  // 3. Por último, paginamos los resultados
  const totalPaginas = Math.ceil(usuariosFiltrados.length / ITEMS_POR_PAGINA);
  const indiceUltimoItem = paginaActual * ITEMS_POR_PAGINA;
  const indicePrimerItem = indiceUltimoItem - ITEMS_POR_PAGINA;
  const usuariosPaginados = usuariosFiltrados.slice(indicePrimerItem, indiceUltimoItem);

  // Función auxiliar para pintar las medallas
  const obtenerEstiloPosicion = (posicion: number) => {
    if (posicion === 1) return { color: "#FFD700", bg: "#FFF9C4" }; // Oro
    if (posicion === 2) return { color: "#C0C0C0", bg: "#F5F5F5" }; // Plata
    if (posicion === 3) return { color: "#CD7F32", bg: "#EFEBE9" }; // Bronce
    return { color: "#666", bg: "#f0f0f0" }; // Resto
  };

  return (
    <div style={{ maxWidth: "1000px", margin: "0 auto" }}>
      
      {/* Cabecera */}
      <div style={{ marginBottom: "30px" }}>
        <h1 style={{ fontSize: "2.5rem", color: "#1e1e1e", marginBottom: "10px", display: "flex", alignItems: "center" }}>
          <Trophy size={36} style={{ marginRight: "15px", color: "#ff4b4b" }} />
          Clasificación Global
        </h1>
        <p style={{ color: "#666", fontSize: "1.1rem" }}>
          Compite con otros desarrolladores y llega a lo más alto del ranking.
        </p>
      </div>

      {/* Controles de Búsqueda y Ordenación */}
      <div style={{ 
        display: "flex", gap: "15px", marginBottom: "30px", 
        backgroundColor: "white", padding: "15px", borderRadius: "10px", 
        boxShadow: "0 2px 5px rgba(0,0,0,0.05)", border: "1px solid #eaeaea",
        justifyContent: "space-between", flexWrap: "wrap"
      }}>
        
        {/* Buscador */}
        <div style={{ flex: "1 1 300px", position: "relative" }}>
          <Search size={20} style={{ position: "absolute", left: "15px", top: "12px", color: "#888" }} />
          <input 
            type="text" 
            placeholder="Buscar por nombre de usuario..." 
            value={busqueda}
            onChange={(e) => {
              setBusqueda(e.target.value);
              setPaginaActual(1); // Reseteo de página seguro
            }}
            style={{ width: "100%", padding: "12px 15px 12px 45px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none" }}
          />
        </div>

        {/* Selector de Orden */}
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <ArrowUpDown size={20} color="#888" />
          <span style={{ color: "#555", fontWeight: "500" }}>Ordenar por:</span>
          <select 
            value={criterioOrden} 
            onChange={(e) => {
              setCriterioOrden(e.target.value);
              setPaginaActual(1); // Reseteo de página seguro
            }}
            style={{ padding: "12px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none", cursor: "pointer", backgroundColor: "white" }}
          >
            <option value="puntos">Puntos Totales</option>
            <option value="retos">Retos Completados</option>
          </select>
        </div>
      </div>

      {/* Tabla de Clasificación */}
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
                const estilo = obtenerEstiloPosicion(usuario.posicionGlobal);
                return (
                  <tr key={usuario.id} style={{ borderBottom: "1px solid #eee", transition: "background-color 0.2s" }} onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#f9f9f9"} onMouseOut={(e) => e.currentTarget.style.backgroundColor = "transparent"}>
                    {/* Columna: Rank */}
                    <td style={{ padding: "15px 20px", textAlign: "center" }}>
                      <div style={{ 
                        display: "inline-flex", alignItems: "center", justifyContent: "center",
                        width: "35px", height: "35px", borderRadius: "50%", 
                        backgroundColor: estilo.bg, color: estilo.color, 
                        fontWeight: "bold", fontSize: "1.1rem"
                      }}>
                        {usuario.posicionGlobal <= 3 ? <Medal size={20} /> : `#${usuario.posicionGlobal}`}
                      </div>
                    </td>
                    
                    {/* Columna: Usuario */}
                    <td style={{ padding: "15px 20px", fontWeight: "bold", color: "#333", fontSize: "1.1rem" }}>
                      {usuario.nombre}
                    </td>
                    
                    {/* Columna: Retos */}
                    <td style={{ padding: "15px 20px", textAlign: "center", color: "#666" }}>
                      {usuario.retos}
                    </td>
                    
                    {/* Columna: Puntos */}
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

      {/* Controles de Paginación */}
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