// frontend/src/pages/Retos.tsx

import { useState, useEffect } from "react";
import { Terminal, Clock, ChevronRight, Search, Filter, ChevronLeft, Wand2, Pencil, Trash2, X, Eye, EyeOff, AlertTriangle, Download, Loader2, CheckCircle2, Plus, FileUp } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import toast from 'react-hot-toast';

const ITEMS_POR_PAGINA = 10;

interface BackendChallenge {
  id: string;
  idCodeWars: string;
  name: string;
  slug: string;
  description: string;
  rank: number;
  isVisible?: boolean; 
  tags?: string[];
  tests?: Record<string, string>; 
}

interface FrontendChallenge {
  id: string;
  titulo: string;
  dificultad: string;
  colorDificultad: string;
  etiquetas: string[];
  descripcion: string;
  descripcionOriginal: string;
  tiempoEstimado: number;
  rangoRaw: number;
  isVisible: boolean; 
  tieneTests: boolean; 
  tests: Record<string, string>; 
}

export default function Retos() {
  const navigate = useNavigate();
  const auth = useAuth();
  const token = auth.user?.access_token;

  // --- VARIABLES DE ENTORNO DINÁMICAS ---
  const baseUrlUsers = import.meta.env.VITE_USER_URL || 'http://localhost:8080';
  const baseUrlChallenges = import.meta.env.VITE_CHALLENGES_URL || import.meta.env.VITE_USER_URL || 'http://localhost:8081';

  const [retos, setRetos] = useState<FrontendChallenge[]>([]);
  const [cargando, setCargando] = useState(true);
  const [isAdmin, setIsAdmin] = useState(false);

  const [busqueda, setBusqueda] = useState("");
  const [dificultadFiltro, setDificultadFiltro] = useState("Todas");
  const [etiquetaFiltro, setEtiquetaFiltro] = useState("Todas");
  const [tiempoFiltro, setTiempoFiltro] = useState("Todos");
  const [testFiltro, setTestFiltro] = useState("Todos");
  const [paginaActual, setPaginaActual] = useState(1);

  const [modalEliminar, setModalEliminar] = useState({ abierto: false, id: "", titulo: "" });
  const [modalGenerar, setModalGenerar] = useState({ abierto: false, id: "", titulo: "" });
  const [modalEditar, setModalEditar] = useState({ abierto: false, id: "" });
  const [modalTestsFaltantes, setModalTestsFaltantes] = useState(false);
  const [modalImportar, setModalImportar] = useState(false);
  const [importIds, setImportIds] = useState<string[]>([""]); 
  const [archivoExcel, setArchivoExcel] = useState<File | null>(null);
  const [modalGenerarReto, setModalGenerarReto] = useState(false);
  const [modalAviso, setModalAviso] = useState({ abierto: false, titulo: "", mensaje: "", recargar: false });

  const [cargandoAccion, setCargandoAccion] = useState(false);
  const [exitoAccion, setExitoAccion] = useState(false);
  const [idiomaTest, setIdiomaTest] = useState("python");
  const [formData, setFormData] = useState({ name: "", description: "", rank: 8, tags: "", isVisible: false, tests: {} as Record<string, string> });

  useEffect(() => {
    const comprobarAdmin = async () => {
      try {
        const res = await fetch(`${baseUrlUsers}/api/users/me`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.ok) {
          const userData = await res.json();
          const role = userData.role?.trim().toLowerCase();
          if (role === "admin") setIsAdmin(true);
        }
      } catch {
        console.error("Error al verificar rol de admin");
      }
    };

    const cargarRetos = async () => {
      try {
        const response = await fetch(`${baseUrlChallenges}/api/challenges`, {
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
            
            if (retoBackend.rank >= 7) { 
                dificultad = "Fácil"; colorDificultad = "#4caf50"; tiempo = 10; 
            } else if (retoBackend.rank <= 4) { 
                dificultad = "Difícil"; colorDificultad = "#ff4b4b"; tiempo = 45; 
            }

            const etiquetasReales = retoBackend.tags && retoBackend.tags.length > 0 
              ? retoBackend.tags 
              : [`${retoBackend.rank} kyu`, "Algoritmos"];

            const diccionarioTests = retoBackend.tests || {};
            const tieneTests = Object.keys(diccionarioTests).length > 0;

            return {
              id: retoBackend.id,
              titulo: retoBackend.name,
              dificultad: dificultad,
              colorDificultad: colorDificultad,
              etiquetas: etiquetasReales,
              descripcion: retoBackend.description.replace(/<[^>]+>/g, '').substring(0, 150) + "...",
              descripcionOriginal: retoBackend.description,
              tiempoEstimado: tiempo,
              rangoRaw: retoBackend.rank,
              isVisible: retoBackend.isVisible !== false,
              tieneTests: tieneTests,
              tests: diccionarioTests 
            };
          });
          setRetos(datosAdaptados);
        }
      } catch (error) {
        console.error("Error conectando con el backend:", error);
      } finally {
        setCargando(false);
      }
    };

    if (token) {
      comprobarAdmin();
      cargarRetos();
    } else if (!auth.isLoading) {
      setCargando(false);
    }
  }, [token, auth.isLoading, baseUrlUsers, baseUrlChallenges]);

  const ejecutarImportar = async () => {
    // --- Lógica si hay un archivo Excel seleccionado ---
    if (archivoExcel) {
      setModalImportar(false);
      setCargandoAccion(true);

      try {
        const dataFormulario = new FormData();
        dataFormulario.append("file", archivoExcel);
        
        const response = await fetch(`${baseUrlChallenges}/api/challenges/import/excel`, {
          method: "POST",
          headers: { 'Authorization': `Bearer ${token}` },
          body: dataFormulario
        });

        setCargandoAccion(false);
        const datos = await response.json();

        if (datos.status === "200") {
          setArchivoExcel(null);
          setImportIds([""]);
          setExitoAccion(true);
          setTimeout(() => { window.location.reload(); }, 1500);
        } 
        else if (datos.status === "207") {
          setArchivoExcel(null);
          setImportIds([""]);
          setModalAviso({ 
              abierto: true, 
              titulo: "Atención - Importación Parcial", 
              mensaje: datos.message, 
              recargar: true
          });
        } 
        else {
          setArchivoExcel(null);
          setModalAviso({ 
              abierto: true, 
              titulo: "Atención", 
              mensaje: datos.message || "El servidor no pudo procesar el Excel. Asegúrate de que el formato es correcto.",
              recargar: false
          });
        }
      } catch {
        setCargandoAccion(false);
        setArchivoExcel(null);
        setModalAviso({ 
            abierto: true, 
            titulo: "Error de Conexión", 
            mensaje: "No se ha podido conectar con el servidor para enviar el archivo.",
            recargar: false
        });
      }
      return;
    }

    // --- Si NO hay Excel, usamos las cajitas de texto ---
    const idsValidos = importIds.map(id => id.trim()).filter(id => id !== "");
    if (idsValidos.length === 0) return;
    
    setModalImportar(false); 
    setCargandoAccion(true);

    try {
      const peticiones = idsValidos.map(id => 
        fetch(`${baseUrlChallenges}/api/challenges/import/${id}`, {
          method: "POST",
          headers: { 'Authorization': `Bearer ${token}` }
        })
      );

      const respuestas = await Promise.all(peticiones);
      setCargandoAccion(false);

      const detallesExitos: string[] = [];
      const detallesErrores: string[] = [];

      for (let i = 0; i < respuestas.length; i++) {
        const res = respuestas[i];
        const id = idsValidos[i];

        if (res.ok) {
          detallesExitos.push(`  • ID: ${id}`);
        } else {
          let errorMsg = "Error desconocido";
          try {
            const datosError = await res.json();
            errorMsg = datosError.message || errorMsg;
          } catch {
            errorMsg = "Error interno del servidor (500)";
          }
          
          if (errorMsg.includes("404 Not Found")) {
            errorMsg = "404 Not Found";
          } else if (errorMsg.includes("from GET")) {
            errorMsg = errorMsg.split("from GET")[0].trim();
          }

          detallesErrores.push(`  • ID: ${id} - ${errorMsg}`);
        }
      }

      let mensajeFinal = "";
      if (detallesExitos.length > 0) {
        mensajeFinal += `✅ Éxitos: ${detallesExitos.length}\n${detallesExitos.join("\n")}\n\n`;
      }
      if (detallesErrores.length > 0) {
        mensajeFinal += `⚠️ Errores: ${detallesErrores.length}\n${detallesErrores.join("\n")}`;
      }
      mensajeFinal = mensajeFinal.trim();

      // Evaluamos los resultados
      if (detallesErrores.length === 0) {
        setImportIds([""]); 
        setExitoAccion(true);
        setTimeout(() => { window.location.reload(); }, 1500);
      } 
      else if (detallesExitos.length === 0) {
        setImportIds([""]);
        setModalAviso({ 
            abierto: true, 
            titulo: "Atención", 
            mensaje: mensajeFinal,
            recargar: false
        });
      } 
      else {
        setImportIds([""]);
        setModalAviso({ 
            abierto: true, 
            titulo: "Importación Parcial", 
            mensaje: mensajeFinal,
            recargar: true
        });
      }

    } catch {
      setCargandoAccion(false); 
      setModalAviso({ 
          abierto: true, 
          titulo: "Error de Conexión", 
          mensaje: "No se ha podido conectar con el servidor al intentar importar.",
          recargar: false
      });
    }
  };

  const ejecutarGenerarReto = async () => {
    setModalAviso({ abierto: true, titulo: "Próximamente", mensaje: "¡La IA construirá un reto desde cero para ti muy pronto!", recargar: false });
    setModalGenerarReto(false);
  };

  const ejecutarEliminar = async () => {
    try {
      const response = await fetch(`${baseUrlChallenges}/api/challenges/${modalEliminar.id}`, {
        method: "DELETE",
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (response.ok) {
        setRetos(retos.filter(r => r.id !== modalEliminar.id));
        setModalEliminar({ abierto: false, id: "", titulo: "" });
      } else {
        setModalEliminar({ abierto: false, id: "", titulo: "" });
        setModalAviso({ abierto: true, titulo: "Error", mensaje: "El servidor no pudo eliminar el reto.", recargar: false });
      }
    } catch {
      setModalEliminar({ abierto: false, id: "", titulo: "" });
      setModalAviso({ abierto: true, titulo: "Error", mensaje: "Error de conexión al intentar eliminar el reto.", recargar: false });
    }
  };

  const ejecutarEditar = async () => {
    const tieneTests = Object.keys(formData.tests || {}).length > 0;
    
    if (formData.isVisible && !tieneTests) {
      setModalEditar({ abierto: false, id: "" });
      setModalAviso({ 
        abierto: true, 
        titulo: "Publicación Bloqueada", 
        mensaje: "No puedes guardar este reto como Público. Debes añadir al menos un test oculto para que los usuarios puedan validar su código.",
        recargar: false 
      });
      return;
    }

    try {
      const tagsArray = formData.tags.split(',').map(tag => tag.trim()).filter(tag => tag !== "");

      const payload: Partial<BackendChallenge> = {
        name: formData.name,
        description: formData.description,
        rank: formData.rank,
        isVisible: formData.isVisible,
        tags: tagsArray
      };
      if (formData.tests && Object.keys(formData.tests).length > 0) {
        payload.tests = formData.tests;
      }

      const response = await fetch(`${baseUrlChallenges}/api/challenges/${modalEditar.id}`, {
        method: "PATCH",
        headers: { 
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      });
      
      if (response.ok) {
        let nuevaDificultad = "Normal";
        let nuevoColor = "#ff9800"; 
        let nuevoTiempo = 20;
        
        // Usamos formData en lugar de payload para evitar los errores de TypeScript
        if (formData.rank >= 7) { nuevaDificultad = "Fácil"; nuevoColor = "#4caf50"; nuevoTiempo = 10; } 
        else if (formData.rank <= 4) { nuevaDificultad = "Difícil"; nuevoColor = "#ff4b4b"; nuevoTiempo = 45; }

        setRetos(retos.map(r => r.id === modalEditar.id ? { 
            ...r, 
            titulo: formData.name,
            descripcionOriginal: formData.description,
            descripcion: formData.description.replace(/<[^>]+>/g, '').substring(0, 150) + "...",
            rangoRaw: formData.rank,
            dificultad: nuevaDificultad,
            colorDificultad: nuevoColor,
            tiempoEstimado: nuevoTiempo,
            isVisible: formData.isVisible,
            etiquetas: tagsArray, // Usamos la variable tagsArray que creaste justo arriba
            tests: payload.tests ? payload.tests : r.tests,
            tieneTests: payload.tests ? true : r.tieneTests
        } : r));

        setModalEditar({ abierto: false, id: "" });
      } else {
        setModalEditar({ abierto: false, id: "" });
        setModalAviso({ abierto: true, titulo: "Error", mensaje: "El servidor no pudo guardar los cambios.", recargar: false });
      }
    } catch {
      setModalEditar({ abierto: false, id: "" });
      setModalAviso({ abierto: true, titulo: "Error", mensaje: "Error de conexión al intentar editar el reto.", recargar: false });
    }
  };

  const toggleVisibilidad = async (reto: FrontendChallenge) => {
    if (!reto.isVisible && !reto.tieneTests) {
      setModalTestsFaltantes(true);
      return;
    }

    try {
      const response = await fetch(`${baseUrlChallenges}/api/challenges/${reto.id}`, {
        method: "PATCH",
        headers: { 
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ isVisible: !reto.isVisible })
      });
      if (response.ok) {
        setRetos(retos.map(r => r.id === reto.id ? { ...r, isVisible: !reto.isVisible } : r));
      }
    } catch {
      setModalAviso({ abierto: true, titulo: "Error", mensaje: "Error al cambiar la visibilidad.", recargar: false });
    }
  };

  const generarTestsConIA = (retoId: string, tituloReto: string) => {
    if (!token) return;

    const peticionIA = fetch(`${baseUrlChallenges}/api/challenges/generate/test/${retoId}`, {
      method: "POST",
      headers: { 'Authorization': `Bearer ${token}` }
    }).then(async (res) => {
      if (!res.ok) throw new Error("Fallo en el backend de IA");
      const data = await res.json();
      
      setRetos(retosActuales => 
        retosActuales.map(r => 
          r.id === retoId 
            ? { ...r, tieneTests: true, tests: data.tests || r.tests } // Si el back te devuelve los tests, ponlos aquí
            : r
        )
      );
      return data;
    });

    toast.promise(
      peticionIA,
      {
        loading: `🧠 Qwen2.5 está escribiendo los tests para "${tituloReto}"... (Esto puede tardar un rato)`,
        success: `¡Tests generados con éxito para "${tituloReto}"! `,
        error: `Hubo un problema al generar los tests para "${tituloReto}" `,
      },
      {
        style: {
          minWidth: '350px',
          backgroundColor: '#1e1e1e',
          color: '#fff',
          border: '1px solid #333',
          fontSize: '0.95rem'
        },
        success: {
          duration: 5000,
          icon: '✅',
        },
      }
    );
  };

  const abrirModalEditar = (reto: FrontendChallenge) => {
    setFormData({ 
      name: reto.titulo, 
      description: reto.descripcionOriginal, 
      rank: reto.rangoRaw,
      tags: reto.etiquetas.join(", "),
      isVisible: reto.isVisible,
      tests: reto.tests || {} 
    });
    setIdiomaTest("python"); 
    setModalEditar({ abierto: true, id: reto.id });
  };

  const todasLasEtiquetas = Array.from(new Set(retos.flatMap(reto => reto.etiquetas)));

  const retosFiltrados = retos.filter((reto) => {
    if (!isAdmin && !reto.isVisible) return false;

    const coincideTexto = reto.titulo.toLowerCase().includes(busqueda.toLowerCase()) || reto.descripcion.toLowerCase().includes(busqueda.toLowerCase());
    const coincideDificultad = dificultadFiltro === "Todas" || reto.dificultad === dificultadFiltro;
    const coincideEtiqueta = etiquetaFiltro === "Todas" || reto.etiquetas.includes(etiquetaFiltro);
    
    let coincideTiempo = true;
    if (tiempoFiltro === "15") coincideTiempo = reto.tiempoEstimado <= 15;
    if (tiempoFiltro === "30") coincideTiempo = reto.tiempoEstimado <= 30;
    if (tiempoFiltro === "mas30") coincideTiempo = reto.tiempoEstimado > 30;

    let coincideTests = true;
    if (isAdmin) {
      if (testFiltro === "Con tests") coincideTests = reto.tieneTests;
      if (testFiltro === "Sin tests") coincideTests = !reto.tieneTests;
    }

    return coincideTexto && coincideDificultad && coincideEtiqueta && coincideTiempo && coincideTests;
  });

  const totalPaginas = Math.ceil(retosFiltrados.length / ITEMS_POR_PAGINA) || 1;
  const indiceUltimoItem = paginaActual * ITEMS_POR_PAGINA;
  const indicePrimerItem = indiceUltimoItem - ITEMS_POR_PAGINA;
  const retosPaginados = retosFiltrados.slice(indicePrimerItem, indiceUltimoItem);

  if (cargando) {
      return <div style={{ textAlign: "center", padding: "50px", fontSize: "1.2rem", color: "#666" }}>Conectando con la Base de Datos... ⏳</div>;
  }

  const estiloBotonAdmin = { background: "white", border: "1px solid #ddd", borderRadius: "6px", padding: "8px", cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", transition: "background-color 0.2s" };
  const overlayStyle: React.CSSProperties = { position: "fixed", top: 0, left: 0, right: 0, bottom: 0, backgroundColor: "rgba(0,0,0,0.6)", zIndex: 1000, display: "flex", justifyContent: "center", alignItems: "center" };
  const modalStyle: React.CSSProperties = { backgroundColor: "white", padding: "30px", borderRadius: "10px", width: "90%", maxWidth: "500px", position: "relative", boxShadow: "0 10px 25px rgba(0,0,0,0.2)", overflow: "hidden" };
  const miniModalStyle: React.CSSProperties = { backgroundColor: "white", padding: "40px 30px", borderRadius: "16px", width: "250px", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", boxShadow: "0 10px 25px rgba(0,0,0,0.2)" };
  const btnCerrarStyle: React.CSSProperties = { position: "absolute", top: "15px", right: "15px", background: "none", border: "none", cursor: "pointer", color: "#888" };

  return (
    <div style={{ maxWidth: "1000px", margin: "0 auto" }}>
      
      {/* ---------------- MINI MODALES GENÉRICOS DE CARGA Y ÉXITO ---------------- */}
      
      {cargandoAccion && (
        <div style={overlayStyle}>
          <div style={miniModalStyle}>
            <style>{`
              @keyframes spin { 100% { transform: rotate(360deg); } }
              .ruleta { animation: spin 1s linear infinite; color: #1e1e1e; }
            `}</style>
            <Loader2 size={48} className="ruleta" style={{ marginBottom: "15px" }} />
            <h3 style={{ margin: 0, color: "#333", fontSize: "1.2rem" }}>Cargando...</h3>
          </div>
        </div>
      )}

      {exitoAccion && (
        <div style={overlayStyle}>
          <div style={miniModalStyle}>
            <CheckCircle2 size={56} color="#4caf50" style={{ marginBottom: "15px" }} />
            <h3 style={{ margin: 0, color: "#4caf50", fontSize: "1.2rem" }}>¡Completado!</h3>
          </div>
        </div>
      )}

      {/* ---------------- MODALES ESTÁNDAR ---------------- */}
      
      {modalAviso.abierto && (
        <div style={overlayStyle}>
          <div style={modalStyle}>
            <button style={btnCerrarStyle} onClick={() => {
                if (modalAviso.recargar) window.location.reload();
                setModalAviso({ abierto: false, titulo: "", mensaje: "", recargar: false });
            }}><X size={24} /></button>
            <h2 style={{ marginTop: 0, color: "#d32f2f", display: "flex", alignItems: "center", gap: "10px" }}><AlertTriangle size={24}/> {modalAviso.titulo}</h2>
            <p style={{ fontSize: "1.05rem", color: "#333", lineHeight: "1.5", marginTop: "15px", whiteSpace: "pre-wrap", maxHeight: "300px", overflowY: "auto" }}>
              {modalAviso.mensaje}
            </p>
            <div style={{ display: "flex", gap: "15px", marginTop: "30px", justifyContent: "flex-end" }}>
              <button onClick={() => {
                  if (modalAviso.recargar) window.location.reload();
                  setModalAviso({ abierto: false, titulo: "", mensaje: "", recargar: false });
              }} style={{ padding: "10px 20px", borderRadius: "6px", border: "none", background: "#d32f2f", color: "white", cursor: "pointer", fontWeight: "bold" }}>Aceptar</button>
            </div>
          </div>
        </div>
      )}

      {modalImportar && (
        <div style={overlayStyle}>
          <div style={{ ...modalStyle, maxHeight: "80vh", overflowY: "auto" }}>
            <button style={btnCerrarStyle} onClick={() => { setModalImportar(false); setImportIds([""]); setArchivoExcel(null); }}><X size={24} /></button>
            <h2 style={{ marginTop: 0, color: "#1e1e1e", display: "flex", alignItems: "center", gap: "10px" }}><Download size={24}/> Importar Retos</h2>
            <p style={{ fontSize: "1.05rem", color: "#555", lineHeight: "1.5", marginTop: "15px", marginBottom: "20px" }}>
              Introduce los IDs o "slugs" de los retos de Codewars, o carga un documento Excel.
            </p>
            
            {/* SECCIÓN 1: Cajitas de Texto */}
            <div style={{ display: "flex", flexDirection: "column", gap: "10px", opacity: archivoExcel ? 0.5 : 1, pointerEvents: archivoExcel ? "none" : "auto" }}>
              {importIds.map((id, index) => (
                <div key={index} style={{ display: "flex", gap: "10px" }}>
                  <input 
                    type="text" 
                    placeholder={`ID del reto ${index + 1}...`} 
                    value={id} 
                    onChange={(e) => {
                      const nuevosIds = [...importIds];
                      nuevosIds[index] = e.target.value;
                      setImportIds(nuevosIds);
                    }} 
                    style={{ flex: 1, padding: "12px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", boxSizing: "border-box", outline: "none" }} 
                  />
                  {importIds.length > 1 && (
                    <button 
                      onClick={() => {
                        const nuevosIds = importIds.filter((_, i) => i !== index);
                        setImportIds(nuevosIds);
                      }}
                      style={{ padding: "0 15px", borderRadius: "6px", border: "1px solid #ff4b4b", background: "white", color: "#ff4b4b", cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center" }}
                      title="Eliminar fila"
                    >
                      <X size={18} />
                    </button>
                  )}
                </div>
              ))}
            </div>

            <div style={{ display: "flex", justifyContent: "flex-start", marginTop: "10px", opacity: archivoExcel ? 0.5 : 1, pointerEvents: archivoExcel ? "none" : "auto" }}>
              <button 
                onClick={() => setImportIds([...importIds, ""])}
                style={{ display: "flex", alignItems: "center", gap: "5px", background: "none", border: "none", color: "#2196f3", fontWeight: "bold", cursor: "pointer", fontSize: "0.95rem", padding: 0 }}
              >
                <Plus size={18} /> Añadir otro ID
              </button>
            </div>

            {/* SECCIÓN 2: Archivo Excel o CSV */}
            <div style={{ marginTop: "25px", borderTop: "1px solid #eee", paddingTop: "20px" }}>
              <label style={{ display: "flex", marginBottom: "10px", fontWeight: "bold", color: "#555", alignItems: "center", gap: "8px" }}>
                <FileUp size={18} /> Importar desde archivo
              </label>
              <input 
                type="file" 
                accept=".xlsx, .xls, .csv" 
                onChange={(e) => {
                  const file = e.target.files ? e.target.files[0] : null;
                  setArchivoExcel(file);
                  if (file) setImportIds([""]); 
                }}
                style={{ 
                  display: "block", width: "100%", padding: "10px", border: "2px dashed #ccc", 
                  borderRadius: "8px", backgroundColor: "#fafafa", cursor: "pointer", fontSize: "0.95rem", color: "#555"
                }}
              />
              <span style={{ display: "block", marginTop: "8px", fontSize: "0.85rem", color: "#888", textAlign: "center" }}>
                Formatos soportados: Excel (.xlsx, .xls) o CSV (.csv)
              </span>
            </div>

            {/* BOTONES DE ACCIÓN */}
            <div style={{ display: "flex", gap: "15px", marginTop: "30px", justifyContent: "flex-end" }}>
              <button onClick={() => { setModalImportar(false); setImportIds([""]); setArchivoExcel(null); }} style={{ padding: "10px 20px", borderRadius: "6px", border: "1px solid #ddd", background: "white", cursor: "pointer", fontWeight: "bold" }}>Cancelar</button>
              
              <button 
                onClick={ejecutarImportar} 
                disabled={!archivoExcel && importIds.filter(id => id.trim() !== "").length === 0} 
                style={{ 
                  padding: "10px 20px", borderRadius: "6px", border: "none", 
                  background: (archivoExcel || importIds.filter(id => id.trim() !== "").length > 0) ? "#1e1e1e" : "#ccc", 
                  color: "white", 
                  cursor: (archivoExcel || importIds.filter(id => id.trim() !== "").length > 0) ? "pointer" : "not-allowed", 
                  fontWeight: "bold", display: "flex", alignItems: "center", gap: "8px" 
                }}
              >
                <Download size={18}/> Importar
              </button>
            </div>
          </div>
        </div>
      )}

      {modalGenerarReto && (
        <div style={overlayStyle}>
          <div style={modalStyle}>
            <button style={btnCerrarStyle} onClick={() => setModalGenerarReto(false)}><X size={24} /></button>
            <h2 style={{ marginTop: 0, color: "#673ab7", display: "flex", alignItems: "center", gap: "10px" }}><Wand2 size={24}/> Generar Reto de Cero</h2>
            <p style={{ fontSize: "1.1rem", color: "#333", lineHeight: "1.5", marginTop: "15px" }}>
              ¿Deseas que la IA actúe como profesora y construya un reto de programación completamente nuevo y original para tus alumnos?
            </p>
            <div style={{ display: "flex", gap: "15px", marginTop: "30px", justifyContent: "flex-end" }}>
              <button onClick={() => setModalGenerarReto(false)} style={{ padding: "10px 20px", borderRadius: "6px", border: "1px solid #ddd", background: "white", cursor: "pointer", fontWeight: "bold" }}>Cancelar</button>
              <button onClick={ejecutarGenerarReto} style={{ padding: "10px 20px", borderRadius: "6px", border: "none", background: "#673ab7", color: "white", cursor: "pointer", fontWeight: "bold", display: "flex", alignItems: "center", gap: "8px" }}><Wand2 size={18}/> Generar</button>
            </div>
          </div>
        </div>
      )}

      {modalTestsFaltantes && (
        <div style={overlayStyle}>
          <div style={modalStyle}>
            <button style={btnCerrarStyle} onClick={() => setModalTestsFaltantes(false)}><X size={24} /></button>
            <h2 style={{ marginTop: 0, color: "#ff9800", display: "flex", alignItems: "center", gap: "10px" }}><AlertTriangle size={24}/> Publicación Bloqueada</h2>
            <p style={{ fontSize: "1.1rem", color: "#333", lineHeight: "1.5", marginTop: "15px" }}>
              ⚠️ No puedes hacer público este reto todavía.
            </p>
            <p style={{ fontSize: "1.05rem", color: "#555", lineHeight: "1.5", marginTop: "10px" }}>
              Debes generar primero los casos de prueba con la IA para que los usuarios puedan validar su código.
            </p>
            <div style={{ display: "flex", gap: "15px", marginTop: "30px", justifyContent: "flex-end" }}>
              <button onClick={() => setModalTestsFaltantes(false)} style={{ padding: "10px 20px", borderRadius: "6px", border: "none", background: "#ff9800", color: "white", cursor: "pointer", fontWeight: "bold" }}>Entendido</button>
            </div>
          </div>
        </div>
      )}

      {modalEliminar.abierto && (
        <div style={overlayStyle}>
          <div style={modalStyle}>
            <button style={btnCerrarStyle} onClick={() => setModalEliminar({ abierto: false, id: "", titulo: "" })}><X size={24} /></button>
            <h2 style={{ marginTop: 0, color: "#d32f2f", display: "flex", alignItems: "center", gap: "10px" }}><Trash2 size={24}/> Eliminar Reto</h2>
            <p style={{ fontSize: "1.1rem", color: "#333", lineHeight: "1.5" }}>
              ¿Está seguro que desea eliminar el reto <strong>{modalEliminar.titulo}</strong>?
            </p>
            <div style={{ display: "flex", gap: "15px", marginTop: "30px", justifyContent: "flex-end" }}>
              <button onClick={() => setModalEliminar({ abierto: false, id: "", titulo: "" })} style={{ padding: "10px 20px", borderRadius: "6px", border: "1px solid #ddd", background: "white", cursor: "pointer", fontWeight: "bold" }}>No, cancelar</button>
              <button onClick={ejecutarEliminar} style={{ padding: "10px 20px", borderRadius: "6px", border: "none", background: "#d32f2f", color: "white", cursor: "pointer", fontWeight: "bold" }}>Sí, eliminar</button>
            </div>
          </div>
        </div>
      )}

      {modalGenerar.abierto && (
        <div style={overlayStyle}>
          <div style={modalStyle}>
            <button style={btnCerrarStyle} onClick={() => setModalGenerar({ abierto: false, id: "", titulo: "" })}><X size={24} /></button>
            <h2 style={{ marginTop: 0, color: "#673ab7", display: "flex", alignItems: "center", gap: "10px" }}><Wand2 size={24}/> Generar Tests</h2>
            <p style={{ fontSize: "1.1rem", color: "#333", lineHeight: "1.5" }}>
              ¿Deseas enviar el reto <strong>{modalGenerar.titulo}</strong> a la IA para generar sus casos de prueba automáticamente?
            </p>
            <div style={{ display: "flex", gap: "15px", marginTop: "30px", justifyContent: "flex-end" }}>
              <button onClick={() => setModalGenerar({ abierto: false, id: "", titulo: "" })} style={{ padding: "10px 20px", borderRadius: "6px", border: "1px solid #ddd", background: "white", cursor: "pointer", fontWeight: "bold" }}>Cancelar</button>
              <button 
                onClick={(e) => {
                  e.currentTarget.innerText = "Iniciando IA...";
                  e.currentTarget.style.opacity = "0.7";
                  generarTestsConIA(modalGenerar.id, modalGenerar.titulo);
                  setTimeout(() => {
                    setModalGenerar({ abierto: false, id: "", titulo: "" });
                  }, 500);
                }} 
                style={{ padding: "10px 20px", borderRadius: "6px", border: "none", background: "#673ab7", color: "white", cursor: "pointer", fontWeight: "bold", transition: "all 0.2s" }}
              >
                Generar Tests
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL EDITAR */}
      {modalEditar.abierto && (
        <div style={overlayStyle}>
          <div style={{ ...modalStyle, maxWidth: "800px", maxHeight: "95vh", overflowY: "auto" }}>
            <button style={btnCerrarStyle} onClick={() => setModalEditar({ abierto: false, id: "" })}><X size={24} /></button>
            <h2 style={{ marginTop: 0, color: "#1e1e1e", display: "flex", alignItems: "center", gap: "10px" }}><Pencil size={24}/> Editar Reto</h2>
            
            <div style={{ display: "flex", flexDirection: "column", gap: "15px", marginTop: "20px" }}>
              <div>
                <label style={{ display: "block", marginBottom: "5px", fontWeight: "bold", color: "#555" }}>Título del Reto</label>
                <input type="text" value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})} style={{ width: "100%", padding: "10px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", boxSizing: "border-box" }} />
              </div>

              <div style={{ display: "flex", gap: "15px" }}>
                <div style={{ flex: 1 }}>
                  <label style={{ display: "block", marginBottom: "5px", fontWeight: "bold", color: "#555" }}>Dificultad (Kyu Codewars)</label>
                  <select value={formData.rank} onChange={e => setFormData({...formData, rank: Number(e.target.value)})} style={{ width: "100%", padding: "10px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", boxSizing: "border-box" }}>
                    <option value={8}>8 Kyu (Muy Fácil)</option>
                    <option value={7}>7 Kyu (Fácil)</option>
                    <option value={6}>6 Kyu (Normal)</option>
                    <option value={5}>5 Kyu (Normal-Avanzado)</option>
                    <option value={4}>4 Kyu (Difícil)</option>
                    <option value={3}>3 Kyu (Muy Difícil)</option>
                  </select>
                </div>
                
                <div style={{ flex: 1 }}>
                  <label style={{ display: "block", marginBottom: "5px", fontWeight: "bold", color: "#555" }}>Estado</label>
                  <div style={{ display: "flex", alignItems: "center", height: "40px", gap: "10px" }}>
                    <input type="checkbox" checked={formData.isVisible} onChange={e => setFormData({...formData, isVisible: e.target.checked})} id="visibleCheck" style={{ width: "20px", height: "20px" }}/>
                    <label htmlFor="visibleCheck" style={{ cursor: "pointer", color: formData.isVisible ? "#4caf50" : "#666", fontWeight: "bold" }}>
                      {formData.isVisible ? "Público (Visible)" : "Oculto (Borrador)"}
                    </label>
                  </div>
                </div>
              </div>

              <div>
                <label style={{ display: "block", marginBottom: "5px", fontWeight: "bold", color: "#555" }}>Etiquetas (Separadas por coma)</label>
                <input type="text" placeholder="Ej: Matemáticas, Arrays, String" value={formData.tags} onChange={e => setFormData({...formData, tags: e.target.value})} style={{ width: "100%", padding: "10px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", boxSizing: "border-box" }} />
              </div>

              <div>
                <label style={{ display: "block", marginBottom: "5px", fontWeight: "bold", color: "#555" }}>Descripción (Soporta HTML)</label>
                <textarea 
                  value={formData.description} 
                  onChange={e => setFormData({...formData, description: e.target.value})} 
                  style={{ width: "100%", padding: "10px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", minHeight: "150px", resize: "vertical", boxSizing: "border-box" }} 
                />
              </div>

              <div style={{ border: "1px solid #ddd", padding: "15px", borderRadius: "8px", backgroundColor: "#fbfbfb", boxSizing: "border-box" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "10px" }}>
                  <label style={{ fontWeight: "bold", color: "#555", margin: 0 }}>Código del Test</label>
                  <select 
                    value={idiomaTest} 
                    onChange={e => setIdiomaTest(e.target.value)}
                    style={{ padding: "6px 12px", borderRadius: "4px", border: "1px solid #ccc", fontSize: "0.9rem", outline: "none", cursor: "pointer", backgroundColor: "white", boxSizing: "border-box" }}
                  >
                    <option value="python">Python</option>
                    <option value="javascript">JavaScript</option>
                    <option value="java">Java</option>
                    <option value="c">C</option>
                  </select>
                </div>

                <textarea 
                  value={formData.tests[idiomaTest] || ""} 
                  onChange={e => setFormData({
                    ...formData, 
                    tests: { ...formData.tests, [idiomaTest]: e.target.value } 
                  })} 
                  placeholder={`Escribe aquí el test para ${idiomaTest}...`}
                  style={{ 
                    width: "100%", 
                    padding: "10px", 
                    borderRadius: "6px", 
                    border: "1px solid #ddd", 
                    fontSize: "0.95rem", 
                    minHeight: "150px", 
                    resize: "vertical",
                    fontFamily: "monospace", 
                    backgroundColor: "white",
                    boxSizing: "border-box"
                  }} 
                />
              </div>

            </div>
            
            <div style={{ display: "flex", gap: "15px", marginTop: "30px", justifyContent: "flex-end" }}>
              <button onClick={() => setModalEditar({ abierto: false, id: "" })} style={{ padding: "10px 20px", borderRadius: "6px", border: "1px solid #ddd", background: "white", cursor: "pointer", fontWeight: "bold" }}>Cancelar</button>
              <button onClick={ejecutarEditar} style={{ padding: "10px 20px", borderRadius: "6px", border: "none", background: "#1e1e1e", color: "white", cursor: "pointer", fontWeight: "bold" }}>Guardar Cambios</button>
            </div>
          </div>
        </div>
      )}
      
      {/* ---------------- FIN MODALES ---------------- */}

      {/* CABECERA CON BOTONES */}
      <div style={{ marginBottom: "25px", display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: "15px" }}>
        <div>
          <h1 style={{ fontSize: "2.5rem", color: "#1e1e1e", marginBottom: "10px", display: "flex", alignItems: "center" }}>
            <Terminal size={36} style={{ marginRight: "15px", color: "#ff4b4b" }} />
            Katas Disponibles
          </h1>
          <p style={{ color: "#666", fontSize: "1.1rem", margin: 0 }}>Elige un reto, escribe tu código y demuestra de lo que eres capaz.</p>
        </div>
        
        {/* Mostramos los botones de creación si es Admin */}
        {isAdmin && (
          <div style={{ display: "flex", gap: "12px", marginTop: "10px" }}>
            <button 
              onClick={() => setModalGenerarReto(true)} 
              style={{ display: "flex", alignItems: "center", backgroundColor: "#673ab7", color: "white", border: "none", padding: "10px 18px", borderRadius: "8px", fontSize: "0.95rem", fontWeight: "bold", cursor: "pointer", transition: "0.2s" }} 
              onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#5e35b1"} 
              onMouseOut={(e) => e.currentTarget.style.backgroundColor = "#673ab7"}
            >
              <Wand2 size={18} style={{ marginRight: "8px" }} /> Generar reto
            </button>
            <button 
              onClick={() => setModalImportar(true)} 
              style={{ display: "flex", alignItems: "center", backgroundColor: "#1e1e1e", color: "white", border: "none", padding: "10px 18px", borderRadius: "8px", fontSize: "0.95rem", fontWeight: "bold", cursor: "pointer", transition: "0.2s" }} 
              onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#333"} 
              onMouseOut={(e) => e.currentTarget.style.backgroundColor = "#1e1e1e"}
            >
              <Download size={18} style={{ marginRight: "8px" }} /> Importar
            </button>
          </div>
        )}
      </div>

      <div style={{ display: "flex", gap: "15px", marginBottom: "30px", backgroundColor: "white", padding: "15px", borderRadius: "10px", boxShadow: "0 2px 5px rgba(0,0,0,0.05)", border: "1px solid #eaeaea", flexWrap: "wrap" }}>
        <div style={{ flex: "1 1 250px", position: "relative" }}>
          <Search size={20} style={{ position: "absolute", left: "15px", top: "12px", color: "#888" }} />
          <input type="text" placeholder="Buscar reto..." value={busqueda} onChange={(e) => { setBusqueda(e.target.value); setPaginaActual(1); }} style={{ width: "100%", padding: "12px 15px 12px 45px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none", boxSizing: "border-box" }} />
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <Filter size={20} color="#888" />
          <select value={dificultadFiltro} onChange={(e) => { setDificultadFiltro(e.target.value); setPaginaActual(1); }} style={{ padding: "12px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none", cursor: "pointer", boxSizing: "border-box" }}>
            <option value="Todas">Cualquier dificultad</option>
            <option value="Fácil">Fácil</option>
            <option value="Normal">Normal</option>
            <option value="Difícil">Difícil</option>
          </select>
        </div>
        <select value={etiquetaFiltro} onChange={(e) => { setEtiquetaFiltro(e.target.value); setPaginaActual(1); }} style={{ padding: "12px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none", cursor: "pointer", boxSizing: "border-box" }}>
          <option value="Todas">Todas las etiquetas</option>
          {todasLasEtiquetas.map(tag => <option key={tag} value={tag}>{tag}</option>)}
        </select>
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <Clock size={20} color="#888" />
          <select value={tiempoFiltro} onChange={(e) => { setTiempoFiltro(e.target.value); setPaginaActual(1); }} style={{ padding: "12px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none", cursor: "pointer", boxSizing: "border-box" }}>
            <option value="Todos">Cualquier tiempo</option>
            <option value="15">Hasta 15 min</option>
            <option value="30">Hasta 30 min</option>
            <option value="mas30">Más de 30 min</option>
          </select>
        </div>
        {/* FILTRO DE TESTS (Solo visible para Admins) */}
        {isAdmin && (
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <select value={testFiltro} onChange={(e) => { setTestFiltro(e.target.value); setPaginaActual(1); }} style={{ padding: "12px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none", cursor: "pointer", boxSizing: "border-box" }}>
              <option value="Todos">Estado de los tests</option>
              <option value="Sin tests">Sin tests generados</option>
              <option value="Con tests">Tests generados</option>
            </select>
          </div>
        )}
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
        {retosFiltrados.length === 0 ? (
          <div style={{ textAlign: "center", padding: "40px", backgroundColor: "white", borderRadius: "10px", color: "#888" }}>No se han encontrado retos con esos filtros.</div>
        ) : (
          retosPaginados.map((reto) => (
            <div key={reto.id} style={{ opacity: reto.isVisible ? 1 : 0.6, backgroundColor: "white", borderRadius: "10px", padding: "25px", boxShadow: "0 4px 6px rgba(0,0,0,0.05)", border: "1px solid", borderColor: reto.isVisible ? "#eaeaea" : "#ff9800", display: "flex", justifyContent: "space-between", alignItems: "center", transition: "transform 0.2s, box-shadow 0.2s" }} onMouseOver={(e) => e.currentTarget.style.transform = "translateY(-3px)"} onMouseOut={(e) => e.currentTarget.style.transform = "translateY(0)"}>
              
              <div style={{ flex: 1 }}>
                <div style={{ display: "flex", alignItems: "center", marginBottom: "10px" }}>
                  <h2 style={{ margin: 0, fontSize: "1.4rem", color: "#333", marginRight: "15px" }}>{reto.titulo}</h2>
                  <span style={{ backgroundColor: reto.colorDificultad, color: "white", padding: "4px 10px", borderRadius: "20px", fontSize: "0.8rem", fontWeight: "bold" }}>{reto.dificultad}</span>
                  
                  {!reto.isVisible && (
                    <span style={{ marginLeft: "10px", backgroundColor: "#fff3e0", color: "#e65100", padding: "4px 10px", borderRadius: "4px", fontSize: "0.8rem", fontWeight: "bold", display: "flex", alignItems: "center" }}>
                      <EyeOff size={14} style={{ marginRight: "5px" }} /> Oculto (Borrador)
                    </span>
                  )}

                  {isAdmin && !reto.tieneTests && (
                    <span style={{ marginLeft: "10px", backgroundColor: "#f3e5f5", color: "#673ab7", padding: "4px 10px", borderRadius: "4px", fontSize: "0.8rem", fontWeight: "bold", display: "flex", alignItems: "center" }}>
                      <Wand2 size={14} style={{ marginRight: "5px" }} /> Sin tests
                    </span>
                  )}
                </div>
                <p style={{ color: "#555", marginBottom: "15px", lineHeight: "1.5" }}>{reto.descripcion}</p>
                <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
                  {reto.etiquetas.map((etiqueta, i) => <span key={i} style={{ backgroundColor: "#f0f0f0", color: "#666", padding: "4px 10px", borderRadius: "4px", fontSize: "0.85rem" }}>#{etiqueta}</span>)}
                  <div style={{ display: "flex", alignItems: "center", color: "#888", fontSize: "0.9rem", marginLeft: "15px" }}><Clock size={16} style={{ marginRight: "5px" }} />{reto.tiempoEstimado} min</div>
                </div>
              </div>

              <div style={{ marginLeft: "30px", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center" }}>
                
                {isAdmin && (
                  <div style={{ display: "flex", gap: "10px", marginBottom: "12px" }}>
                    <button title={reto.isVisible ? "Ocultar reto" : "Publicar reto"} onClick={() => toggleVisibilidad(reto)} style={estiloBotonAdmin} onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#f0f0f0"} onMouseOut={(e) => e.currentTarget.style.backgroundColor = "white"}>
                      {reto.isVisible ? <Eye size={18} color="#2196f3" /> : <EyeOff size={18} color="#ff9800" />}
                    </button>
                    <button title="Generar tests con IA" onClick={() => setModalGenerar({ abierto: true, id: reto.id, titulo: reto.titulo })} style={estiloBotonAdmin} onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#f0f0f0"} onMouseOut={(e) => e.currentTarget.style.backgroundColor = "white"}><Wand2 size={18} color="#673ab7" /></button>
                    <button title="Editar reto" onClick={() => abrirModalEditar(reto)} style={estiloBotonAdmin} onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#f0f0f0"} onMouseOut={(e) => e.currentTarget.style.backgroundColor = "white"}><Pencil size={18} color="#555" /></button>
                    <button title="Eliminar reto" onClick={() => setModalEliminar({ abierto: true, id: reto.id, titulo: reto.titulo })} style={estiloBotonAdmin} onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#ffebee"} onMouseOut={(e) => e.currentTarget.style.backgroundColor = "white"}><Trash2 size={18} color="#d32f2f" /></button>
                  </div>
                )}

                <button onClick={() => navigate(`/entrenar/${reto.id}`)} style={{ backgroundColor: "#1e1e1e", color: "white", border: "none", padding: "12px 25px", borderRadius: "8px", fontSize: "1rem", fontWeight: "bold", cursor: "pointer", display: "flex", alignItems: "center" }}>
                  Entrenar <ChevronRight size={20} style={{ marginLeft: "5px" }} />
                </button>
              </div>

            </div>
          ))
        )}
      </div>

      {totalPaginas > 1 && (
        <div style={{ display: "flex", justifyContent: "center", alignItems: "center", marginTop: "40px", gap: "20px" }}>
          <button onClick={() => setPaginaActual(p => p - 1)} disabled={paginaActual === 1} style={{ display: "flex", alignItems: "center", padding: "10px 15px", borderRadius: "6px", border: "1px solid #ddd", backgroundColor: paginaActual === 1 ? "#f5f5f5" : "white", color: paginaActual === 1 ? "#aaa" : "#333", cursor: paginaActual === 1 ? "not-allowed" : "pointer" }}>
            <ChevronLeft size={18} style={{ marginRight: "5px" }} /> Anterior
          </button>
          <span style={{ fontSize: "1rem", color: "#555", fontWeight: "500" }}>Página {paginaActual} de {totalPaginas}</span>
          <button onClick={() => setPaginaActual(p => p + 1)} disabled={paginaActual === totalPaginas} style={{ display: "flex", alignItems: "center", padding: "10px 15px", borderRadius: "6px", border: "1px solid #ddd", backgroundColor: paginaActual === totalPaginas ? "#f5f5f5" : "white", color: paginaActual === totalPaginas ? "#aaa" : "#333", cursor: paginaActual === totalPaginas ? "not-allowed" : "pointer" }}>
            Siguiente <ChevronRight size={18} style={{ marginLeft: "5px" }} />
          </button>
        </div>
      )}
    </div>
  );
}