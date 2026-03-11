import { useState, useEffect } from "react";
import { Terminal, Clock, ChevronRight, Search, Filter, ChevronLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "react-oidc-context";

const ITEMS_POR_PAGINA = 4;

interface BackendChallenge {
  id: string;
  name: string;
  slug: string;
  description: string;
  rank: number;
}

interface FrontendChallenge {
  id: string;
  titulo: string;
  dificultad: string;
  colorDificultad: string;
  etiquetas: string[];
  descripcion: string;
  tiempoEstimado: number;
}

export default function Retos() {
  const navigate = useNavigate();
  
  const auth = useAuth();
  console.log("Objeto auth completo:", auth);
  const token = auth.user?.access_token;
  console.log("Token extraído:", token);

  const [retos, setRetos] = useState<FrontendChallenge[]>([]);
  const [cargando, setCargando] = useState(true);

  const [busqueda, setBusqueda] = useState("");
  const [dificultadFiltro, setDificultadFiltro] = useState("Todas");
  const [etiquetaFiltro, setEtiquetaFiltro] = useState("Todas");
  const [tiempoFiltro, setTiempoFiltro] = useState("Todos");
  const [paginaActual, setPaginaActual] = useState(1);

  useEffect(() => {
    const cargarRetos = async () => {
      try {
        const response = await fetch('http://localhost:8081/api/challenges/', {
          headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
          }
        });

        if (response.ok) {
          const datosBackend: BackendChallenge[] = await response.json();
          
          
          const datosAdaptados = datosBackend.map((retoBackend) => {
            let dificultad = "Normal";
            let colorDificultad = "#ff9800"; 
            let tiempo = 20;
            
            // Los kyu de Codewars van al revés: 8 es fácil, 1 es difícil
            if (retoBackend.rank >= 7) { 
                dificultad = "Fácil"; colorDificultad = "#4caf50"; tiempo = 10; 
            } else if (retoBackend.rank <= 4) { 
                dificultad = "Difícil"; colorDificultad = "#ff4b4b"; tiempo = 45; 
            }

            return {
              id: retoBackend.id,
              titulo: retoBackend.name,
              dificultad: dificultad,
              colorDificultad: colorDificultad,
              etiquetas: [`${retoBackend.rank} kyu`, "Algoritmos"], 
              descripcion: retoBackend.description.replace(/<[^>]+>/g, '').substring(0, 150) + "...",
              tiempoEstimado: tiempo
            };
          });

          setRetos(datosAdaptados);
        } else {
          console.error("Error al cargar retos:", response.status);
        }
      } catch (error) {
        console.error("Error conectando con el backend:", error);
      } finally {
        setCargando(false);
      }
    };

    // Solo llamamos al backend si tenemos el token
    if (token) {
      cargarRetos();
    } else if (!auth.isLoading) {
      setCargando(false);
    }
  }, [token, auth.isLoading]);

  const todasLasEtiquetas = Array.from(new Set(retos.flatMap(reto => reto.etiquetas)));

  const retosFiltrados = retos.filter((reto) => {
    const coincideTexto = reto.titulo.toLowerCase().includes(busqueda.toLowerCase()) || reto.descripcion.toLowerCase().includes(busqueda.toLowerCase());
    const coincideDificultad = dificultadFiltro === "Todas" || reto.dificultad === dificultadFiltro;
    const coincideEtiqueta = etiquetaFiltro === "Todas" || reto.etiquetas.includes(etiquetaFiltro);
    
    let coincideTiempo = true;
    if (tiempoFiltro === "15") coincideTiempo = reto.tiempoEstimado <= 15;
    if (tiempoFiltro === "30") coincideTiempo = reto.tiempoEstimado <= 30;
    if (tiempoFiltro === "mas30") coincideTiempo = reto.tiempoEstimado > 30;

    return coincideTexto && coincideDificultad && coincideEtiqueta && coincideTiempo;
  });

  const totalPaginas = Math.ceil(retosFiltrados.length / ITEMS_POR_PAGINA) || 1;
  const indiceUltimoItem = paginaActual * ITEMS_POR_PAGINA;
  const indicePrimerItem = indiceUltimoItem - ITEMS_POR_PAGINA;
  const retosPaginados = retosFiltrados.slice(indicePrimerItem, indiceUltimoItem);

  if (cargando) {
      return (
          <div style={{ textAlign: "center", padding: "50px", fontSize: "1.2rem", color: "#666" }}>
              Conectando con la Base de Datos... ⏳
          </div>
      );
  }

  return (
    <div style={{ maxWidth: "1000px", margin: "0 auto" }}>
      <div style={{ marginBottom: "25px" }}>
        <h1 style={{ fontSize: "2.5rem", color: "#1e1e1e", marginBottom: "10px", display: "flex", alignItems: "center" }}>
          <Terminal size={36} style={{ marginRight: "15px", color: "#ff4b4b" }} />
          Katas Disponibles
        </h1>
        <p style={{ color: "#666", fontSize: "1.1rem" }}>
          Elige un reto, escribe tu código y demuestra de lo que eres capaz.
        </p>
      </div>

      <div style={{ display: "flex", gap: "15px", marginBottom: "30px", backgroundColor: "white", padding: "15px", borderRadius: "10px", boxShadow: "0 2px 5px rgba(0,0,0,0.05)", border: "1px solid #eaeaea", flexWrap: "wrap" }}>
        <div style={{ flex: "1 1 250px", position: "relative" }}>
          <Search size={20} style={{ position: "absolute", left: "15px", top: "12px", color: "#888" }} />
          <input 
            type="text" 
            placeholder="Buscar reto..." 
            value={busqueda} 
            onChange={(e) => { setBusqueda(e.target.value); setPaginaActual(1); }} 
            style={{ width: "100%", padding: "12px 15px 12px 45px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none" }} 
          />
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <Filter size={20} color="#888" />
          <select 
            value={dificultadFiltro} 
            onChange={(e) => { setDificultadFiltro(e.target.value); setPaginaActual(1); }} 
            style={{ padding: "12px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none", cursor: "pointer" }}
          >
            <option value="Todas">Cualquier dificultad</option>
            <option value="Fácil">Fácil</option>
            <option value="Normal">Normal</option>
            <option value="Difícil">Difícil</option>
          </select>
        </div>

        <select 
          value={etiquetaFiltro} 
          onChange={(e) => { setEtiquetaFiltro(e.target.value); setPaginaActual(1); }} 
          style={{ padding: "12px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none", cursor: "pointer" }}
        >
          <option value="Todas">Todas las etiquetas</option>
          {todasLasEtiquetas.map(tag => <option key={tag} value={tag}>{tag}</option>)}
        </select>

        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <Clock size={20} color="#888" />
          <select 
            value={tiempoFiltro} 
            onChange={(e) => { setTiempoFiltro(e.target.value); setPaginaActual(1); }} 
            style={{ padding: "12px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none", cursor: "pointer" }}
          >
            <option value="Todos">Cualquier tiempo</option>
            <option value="15">Hasta 15 min</option>
            <option value="30">Hasta 30 min</option>
            <option value="mas30">Más de 30 min</option>
          </select>
        </div>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
        {retosFiltrados.length === 0 ? (
          <div style={{ textAlign: "center", padding: "40px", backgroundColor: "white", borderRadius: "10px", color: "#888" }}>
            No se han encontrado retos con esos filtros.
          </div>
        ) : (
          retosPaginados.map((reto) => (
            <div key={reto.id} style={{ backgroundColor: "white", borderRadius: "10px", padding: "25px", boxShadow: "0 4px 6px rgba(0,0,0,0.05)", border: "1px solid #eaeaea", display: "flex", justifyContent: "space-between", alignItems: "center", transition: "transform 0.2s, box-shadow 0.2s" }} onMouseOver={(e) => e.currentTarget.style.transform = "translateY(-3px)"} onMouseOut={(e) => e.currentTarget.style.transform = "translateY(0)"}>
              <div style={{ flex: 1 }}>
                <div style={{ display: "flex", alignItems: "center", marginBottom: "10px" }}>
                  <h2 style={{ margin: 0, fontSize: "1.4rem", color: "#333", marginRight: "15px" }}>{reto.titulo}</h2>
                  <span style={{ backgroundColor: reto.colorDificultad, color: "white", padding: "4px 10px", borderRadius: "20px", fontSize: "0.8rem", fontWeight: "bold" }}>{reto.dificultad}</span>
                </div>
                <p style={{ color: "#555", marginBottom: "15px", lineHeight: "1.5" }}>{reto.descripcion}</p>
                <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
                  {reto.etiquetas.map(etiqueta => <span key={etiqueta} style={{ backgroundColor: "#f0f0f0", color: "#666", padding: "4px 10px", borderRadius: "4px", fontSize: "0.85rem" }}>#{etiqueta}</span>)}
                  <div style={{ display: "flex", alignItems: "center", color: "#888", fontSize: "0.9rem", marginLeft: "15px" }}><Clock size={16} style={{ marginRight: "5px" }} />{reto.tiempoEstimado} min</div>
                </div>
              </div>
              <div style={{ marginLeft: "30px", display: "flex", flexDirection: "column", alignItems: "center" }}>
                <button 
                  onClick={() => navigate(`/entrenar/${reto.id}`)}
                  style={{ backgroundColor: "#1e1e1e", color: "white", border: "none", padding: "12px 25px", borderRadius: "8px", fontSize: "1rem", fontWeight: "bold", cursor: "pointer", display: "flex", alignItems: "center" }}>
                  Entrenar <ChevronRight size={20} style={{ marginLeft: "5px" }} />
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      {totalPaginas > 1 && (
        <div style={{ display: "flex", justifyContent: "center", alignItems: "center", marginTop: "40px", gap: "20px" }}>
          <button 
            onClick={() => setPaginaActual(p => p - 1)} 
            disabled={paginaActual === 1}
            style={{ display: "flex", alignItems: "center", padding: "10px 15px", borderRadius: "6px", border: "1px solid #ddd", backgroundColor: paginaActual === 1 ? "#f5f5f5" : "white", color: paginaActual === 1 ? "#aaa" : "#333", cursor: paginaActual === 1 ? "not-allowed" : "pointer", transition: "background-color 0.2s" }}
          >
            <ChevronLeft size={18} style={{ marginRight: "5px" }} /> Anterior
          </button>
          <span style={{ fontSize: "1rem", color: "#555", fontWeight: "500" }}>
            Página {paginaActual} de {totalPaginas}
          </span>
          <button 
            onClick={() => setPaginaActual(p => p + 1)} 
            disabled={paginaActual === totalPaginas}
            style={{ display: "flex", alignItems: "center", padding: "10px 15px", borderRadius: "6px", border: "1px solid #ddd", backgroundColor: paginaActual === totalPaginas ? "#f5f5f5" : "white", color: paginaActual === totalPaginas ? "#aaa" : "#333", cursor: paginaActual === totalPaginas ? "not-allowed" : "pointer", transition: "background-color 0.2s" }}
          >
            Siguiente <ChevronRight size={18} style={{ marginLeft: "5px" }} />
          </button>
        </div>
      )}
    </div>
  );
}